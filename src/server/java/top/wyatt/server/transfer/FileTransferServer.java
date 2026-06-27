package top.wyatt.server.transfer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.wyatt.CipheredResourceLoader;
import top.wyatt.server.config.CrlConfig;
import top.wyatt.server.config.CrlPackEntry;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;

/**
 * 文件传输服务器 —— 管理线程池，处理客户端下载请求。
 * 每个传输请求分配独立线程，确保多用户并发不会互相阻塞。
 */
public class FileTransferServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CipheredResourceLoader.MOD_ID + "-transfer");

    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 20;
    private static final long KEEP_ALIVE_SECONDS = 60;

    private final ExecutorService executor;
    private final PortAllocator portAllocator;
    private final CrlConfig config;

    public FileTransferServer(CrlConfig config) {
        this.config = config;
        this.portAllocator = new PortAllocator(config.getTransferPort());
        this.executor = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "CRL-Transfer-" + System.currentTimeMillis() % 100000);
                    t.setDaemon(true);
                    return t;
                }
        );
        LOGGER.info("文件传输服务已启动，线程池: core={}, max={}", CORE_POOL_SIZE, MAX_POOL_SIZE);
    }

    /**
     * 处理下载请求，返回分配的端口号。
     * 传输在独立线程中异步执行。
     *
     * @param packId 资源包ID
     * @return 分配的端口号，-1 表示失败
     */
    public int requestDownload(String packId) {
        CrlPackEntry pack = config.getPackById(packId);
        if (pack == null) {
            LOGGER.warn("下载请求失败：找不到资源包 {}", packId);
            return -1;
        }

        Path packFile = CrlConfig.findPackFile(config.getCrlPacksDir(), pack.getFileName());
        if (packFile == null) {
            LOGGER.error("下载请求失败：文件不存在 {} (目录: {})",
                    config.getCrlPacksDir().resolve(pack.getFileName()), config.getCrlPacksDir());
            return -1;
        }

        int port;
        ServerSocket serverSocket;
        try {
            port = portAllocator.allocate();
            // 在返回端口前创建 ServerSocket，确保客户端连接时端口已监听
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
        } catch (IOException e) {
            LOGGER.error("端口分配或监听失败", e);
            return -1;
        }

        try {
            long fileSize = Files.size(packFile);
            FileTransferSession session = new FileTransferSession(
                    port, packId, pack.getFileName(), pack.getSha256(), fileSize, packFile, portAllocator,
                    config.getRateLimitMbPerSec(), serverSocket
            );
            executor.submit(session);
            LOGGER.info("已提交传输任务: packId={}, port={}, file={}", packId, port, pack.getFileName());
            return port;
        } catch (Exception e) {
            LOGGER.error("创建传输任务失败: packId={}", packId, e);
            try { serverSocket.close(); } catch (IOException ignored) {}
            portAllocator.release(port);
            return -1;
        }
    }

    /**
     * 获取当前活跃的传输任务数。
     */
    public int getActiveTransferCount() {
        if (executor instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) executor).getActiveCount();
        }
        return 0;
    }

    /**
     * 关闭传输服务。
     */
    public void shutdown() {
        LOGGER.info("正在关闭文件传输服务...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("文件传输服务已关闭");
    }
}