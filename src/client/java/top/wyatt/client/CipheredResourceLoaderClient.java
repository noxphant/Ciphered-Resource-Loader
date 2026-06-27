package top.wyatt.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.wyatt.client.command.ClientCrlCommand;
import top.wyatt.client.config.ClientUiConfig;
import top.wyatt.client.download.DownloadRecordManager;
import top.wyatt.client.net.ClientPackSyncHandler;
import top.wyatt.client.pack.ClientPackManager;
import top.wyatt.client.pack.ServerPackInfo;
import top.wyatt.core.ModConstants;
import top.wyatt.core.key.KeyManager;
import top.wyatt.core.memory.DirectBufferPool;

public class CipheredResourceLoaderClient implements ClientModInitializer {
    private static final String MOD_ID = ModConstants.MOD_ID;
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID + "-client");

    private static KeyManager clientKeyManager;
    private static DirectBufferPool clientBufferPool;
    private static ClientPackManager clientPackManager;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[{}] 客户端模组初始化开始", MOD_ID);

        // 1. 初始化核心安全组件
        initCoreSecurityComponents();
        // 2. 初始化客户端资源包管理器
        initClientPackManager();
        // 3. 初始化客户端UI配置
        initClientUiConfig();
        // 4. 初始化下载记录管理器
        initDownloadRecordManager();
        // 5. 注册客户端命令
        registerClientCommands();
        // 6. 注册服务端网络包接收处理器
        registerNetworkHandlers();
        // 7. 注册客户端连接生命周期
        registerConnectionLifecycle();

        LOGGER.info("[{}] 客户端模组初始化完成", MOD_ID);
    }

    private void initClientUiConfig() {
        ClientUiConfig.getInstance();
        LOGGER.debug("客户端UI配置初始化完成");
    }

    private void initDownloadRecordManager() {
        DownloadRecordManager.getInstance();
        LOGGER.debug("下载记录管理器初始化完成");
    }

    private void registerClientCommands() {
        ClientCrlCommand.register();
        LOGGER.debug("客户端命令注册完成");
    }

    /**
     * 初始化核心安全组件
     * 密钥管理、堆外内存池均为会话级，退出游戏自动销毁
     */
    private void initCoreSecurityComponents() {
        clientKeyManager = new KeyManager();
        clientBufferPool = new DirectBufferPool();
        LOGGER.debug("核心安全组件初始化完成");
    }

    /**
     * 初始化客户端资源包管理器
     * 启动时扫描本地加密资源包，管理下载与加载状态
     */
    private void initClientPackManager() {
        clientPackManager = new ClientPackManager();
        clientPackManager.scanLocalEncryptedPacks();
        LOGGER.debug("客户端资源包管理器初始化完成，本地扫描完成");
    }

    /**
     * 注册服务端网络通信接收处理器
     * 处理资源包列表同步、下载校验、密钥下发
     */
    private void registerNetworkHandlers() {
        ClientPackSyncHandler.registerReceiver();
        LOGGER.debug("客户端网络接收处理器注册完成");
    }

    /**
     * 注册服务器连接生命周期
     * 断开服务器时自动清理服务端下发的临时密钥与资源包状态
     */
    private void registerConnectionLifecycle() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;

            String serverAddress = client.getServer() != null ? "local" :
                    handler.getConnection().getAddress().toString();

            ServerPackInfo packInfo = ServerPackInfo.getForServer(serverAddress);
            if (packInfo != null && packInfo.getPackCount() > 0) {
                int requiredLoaded = 0;
                int requiredTotal = packInfo.getRequiredCount();
                int optionalLoaded = 0;
                int optionalTotal = packInfo.getOptionalCount();

                for (ServerPackInfo.PackEntry pack : packInfo.getPacks()) {
                    if (pack.installed()) {
                        if (pack.required()) {
                            requiredLoaded++;
                        } else {
                            optionalLoaded++;
                        }
                    }
                }

                String statusMessage = String.format("§a必选资源包：%d/%d\n§e选装资源包：%d/%d",
                        requiredLoaded, requiredTotal, optionalLoaded, optionalTotal);

                player.sendMessage(Text.literal(statusMessage), false);
                LOGGER.info("发送资源包状态信息: {}", statusMessage);
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            clientKeyManager.clearServerSessionKeys();
            clientPackManager.clearServerPackStates();
            LOGGER.debug("已断开服务器，清理服务端资源包与会话密钥");
        });
    }

    // ==================== 全局访问入口 ====================

    public static KeyManager getClientKeyManager() {
        return clientKeyManager;
    }

    public static DirectBufferPool getClientBufferPool() {
        return clientBufferPool;
    }

    public static ClientPackManager getClientPackManager() {
        return clientPackManager;
    }
}