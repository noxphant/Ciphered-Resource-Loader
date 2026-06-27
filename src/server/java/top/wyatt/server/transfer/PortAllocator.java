package top.wyatt.server.transfer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.wyatt.CipheredResourceLoader;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.Set;

/**
 * 端口分配器 —— 支持固定端口和自动分配两种模式。
 * <p>
 * 固定端口: 配置中 transferPort &gt; 0 时，固定使用该端口。若端口被占用则分配失败。
 * 自动分配: transferPort = 0 时，从 2500 起递增查找可用端口。
 * <p>
 * 线程安全，支持并发分配与释放。
 */
public class PortAllocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(CipheredResourceLoader.MOD_ID + "-transfer");
    private static final int AUTO_START_PORT = 2500;
    private static final int MAX_PORT = 65535;

    private final int fixedPort;
    private final Set<Integer> allocatedPorts = new HashSet<>();

    /**
     * @param fixedPort 固定端口，0 表示自动分配
     */
    public PortAllocator(int fixedPort) {
        this.fixedPort = fixedPort > 0 && fixedPort <= MAX_PORT ? fixedPort : 0;
        if (this.fixedPort > 0) {
            LOGGER.info("传输端口已固定为: {}", this.fixedPort);
        } else {
            LOGGER.info("传输端口模式: 自动分配 (起始 {})", AUTO_START_PORT);
        }
    }

    /**
     * 分配一个可用端口。
     *
     * @return 可用端口号
     * @throws IOException 所有端口均被占用时抛出
     */
    public synchronized int allocate() throws IOException {
        int startPort = fixedPort > 0 ? fixedPort : AUTO_START_PORT;

        for (int port = startPort; port <= MAX_PORT; port++) {
            if (allocatedPorts.contains(port)) {
                continue;
            }
            try (ServerSocket probe = new ServerSocket(port)) {
                probe.setReuseAddress(true);
                allocatedPorts.add(port);
                LOGGER.debug("端口 {} 已分配", port);
                return port;
            } catch (IOException ignored) {
                // 若为固定端口且被占用，直接失败，不继续尝试
                if (fixedPort > 0) {
                    throw new IOException("固定端口 " + fixedPort + " 已被占用，请更换端口或检查防火墙设置");
                }
                // 自动模式下继续尝试下一个
            }
        }
        throw new IOException("无可用的传输端口 (范围 " + startPort + "-" + MAX_PORT + ")");
    }

    /**
     * 释放端口。
     */
    public synchronized void release(int port) {
        allocatedPorts.remove(port);
        LOGGER.debug("端口 {} 已释放", port);
    }
}