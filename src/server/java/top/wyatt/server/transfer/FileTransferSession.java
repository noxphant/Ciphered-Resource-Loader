package top.wyatt.server.transfer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.wyatt.CipheredResourceLoader;
import top.wyatt.core.crypto.HashUtil;
import top.wyatt.server.config.CrlConfig;
import top.wyatt.server.config.CrlPackEntry;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 单文件传输会话 —— 在指定端口上监听客户端连接，发送文件并传递哈希值。
 * 每个会话独立线程运行，传输完成后自动释放端口。
 */
public class FileTransferSession implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(CipheredResourceLoader.MOD_ID + "-transfer");
    private static final int ACCEPT_TIMEOUT_MS = 30_000;
    private static final int BUFFER_SIZE = 65536;

    private final int port;
    private final String packId;
    private final String fileName;
    private final String expectedHash;
    private final long fileSize;
    private final Path packFile;
    private final PortAllocator portAllocator;
    private final double rateLimitMbPerSec;
    private final ServerSocket serverSocket;  // 预先创建，确保端口已监听

    public FileTransferSession(int port, String packId, String fileName,
                               String expectedHash, long fileSize, Path packFile,
                               PortAllocator portAllocator, double rateLimitMbPerSec,
                               ServerSocket serverSocket) {
        this.port = port;
        this.packId = packId;
        this.fileName = fileName;
        this.expectedHash = expectedHash;
        this.fileSize = fileSize;
        this.packFile = packFile;
        this.portAllocator = portAllocator;
        this.rateLimitMbPerSec = rateLimitMbPerSec > 0 ? rateLimitMbPerSec : 0;
        this.serverSocket = serverSocket;
    }

    @Override
    public void run() {
        LOGGER.info("[{}] 传输会话启动，端口: {}，文件: {} ({} bytes)", packId, port, fileName, fileSize);

        try {
            serverSocket.setSoTimeout(ACCEPT_TIMEOUT_MS);
            LOGGER.debug("[{}] 等待客户端连接，端口: {}，超时: {}ms", packId, port, ACCEPT_TIMEOUT_MS);

            try (Socket clientSocket = serverSocket.accept()) {
                clientSocket.setSoTimeout(30_000);
                LOGGER.info("[{}] 客户端已连接: {}", packId, clientSocket.getInetAddress());

                // 计算实际文件哈希（服务端校验）
                String actualHash = HashUtil.sha256Hex(packFile);
                if (!actualHash.equalsIgnoreCase(expectedHash)) {
                    LOGGER.error("[{}] 服务端文件哈希不匹配！期望: {}，实际: {}",
                            packId, expectedHash, actualHash);
                    sendError(clientSocket, "FILE_HASH_MISMATCH");
                    return;
                }

                long actualSize = Files.size(packFile);
                LOGGER.debug("[{}] 文件校验通过，大小: {} bytes，哈希: {}", packId, actualSize, actualHash);

                // 协议： [8字节: fileSize] [文件数据] [64字节: SHA-256哈希]
                try (OutputStream out = new BufferedOutputStream(clientSocket.getOutputStream(), BUFFER_SIZE);
                     InputStream fileIn = new BufferedInputStream(Files.newInputStream(packFile), BUFFER_SIZE)) {

                    DataOutputStream dataOut = new DataOutputStream(out);

                    // 写文件大小
                    dataOut.writeLong(actualSize);

                    // 写文件数据（带限速）
                    byte[] buffer = new byte[BUFFER_SIZE];
                    long totalSent = 0;
                    int bytesRead;
                    long windowStart = System.nanoTime();
                    long windowBytes = 0;
                    // 每秒允许的字节数
                    long bytesPerSec = rateLimitMbPerSec > 0 ? (long) (rateLimitMbPerSec * 1024 * 1024) : Long.MAX_VALUE;

                    while ((bytesRead = fileIn.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        totalSent += bytesRead;

                        if (rateLimitMbPerSec > 0) {
                            windowBytes += bytesRead;
                            long elapsedNs = System.nanoTime() - windowStart;
                            // 每发送 256KB 或 100ms 检查一次限速
                            if (windowBytes >= 262144 || elapsedNs >= 100_000_000L) {
                                long expectedNs = windowBytes * 1_000_000_000L / bytesPerSec;
                                if (elapsedNs < expectedNs) {
                                    long sleepMs = (expectedNs - elapsedNs) / 1_000_000L;
                                    if (sleepMs > 0) {
                                        try { Thread.sleep(sleepMs); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); break; }
                                    }
                                }
                                windowStart = System.nanoTime();
                                windowBytes = 0;
                            }
                        }
                    }
                    out.flush();

                    // 写 SHA-256 哈希（64字符十六进制）
                    byte[] hashBytes = actualHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                    out.write(hashBytes);
                    out.flush();

                    LOGGER.info("[{}] 文件传输完成，发送 {} bytes，哈希: {}", packId, totalSent, actualHash);
                }

            } catch (SocketTimeoutException e) {
                LOGGER.warn("[{}] 等待客户端连接超时 ({}ms)，端口: {}", packId, ACCEPT_TIMEOUT_MS, port);
            }

        } catch (IOException e) {
            LOGGER.error("[{}] 传输异常，端口: {}", packId, port, e);
        } finally {
            try { serverSocket.close(); } catch (IOException ignored) {}
            portAllocator.release(port);
            LOGGER.debug("[{}] 传输会话结束，端口 {} 已释放", packId, port);
        }
    }

    private void sendError(Socket clientSocket, String errorCode) {
        try {
            OutputStream out = clientSocket.getOutputStream();
            // 错误标记：fileSize = -1 表示错误
            DataOutputStream dataOut = new DataOutputStream(out);
            dataOut.writeLong(-1);
            byte[] errorBytes = errorCode.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            dataOut.writeInt(errorBytes.length);
            out.write(errorBytes);
            out.flush();
        } catch (IOException ignored) {
            // 客户端可能已断开
        }
    }
}