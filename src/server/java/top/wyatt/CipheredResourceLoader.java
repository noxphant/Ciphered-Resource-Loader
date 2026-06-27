package top.wyatt;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.command.CommandManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.wyatt.core.ModConstants;
import top.wyatt.core.net.NetworkConstants;
import top.wyatt.server.command.CrlCommand;
import top.wyatt.server.config.CrlConfig;
import top.wyatt.server.net.ServerPackSyncSender;
import top.wyatt.server.transfer.FileTransferServer;
import top.wyatt.server.validation.RequiredPackValidator;

public class CipheredResourceLoader implements DedicatedServerModInitializer {
    public static final String MOD_ID = ModConstants.MOD_ID;
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static CrlConfig crlConfig;
    private static RequiredPackValidator packValidator;
    private static FileTransferServer fileTransferServer;

    @Override
    public void onInitializeServer() {
        LOGGER.info("[{}] 服务端模组初始化开始", MOD_ID);

        loadCrlConfig();
        initPackValidator();
        initFileTransferServer();
        registerCommands();
        registerNetworkChannels();
        registerPlayerLifecycleEvents();

        LOGGER.info("[{}] 服务端模组初始化完成，共加载 {} 个资源包配置",
                MOD_ID, crlConfig.getPackCount());
    }

    private void loadCrlConfig() {
        crlConfig = new CrlConfig();
        try {
            crlConfig.loadOrCreateDefault();
            LOGGER.debug("CRL配置文件加载成功");
        } catch (Exception e) {
            LOGGER.error("CRL配置文件加载失败，将使用空配置", e);
        }
    }

    private void initPackValidator() {
        packValidator = new RequiredPackValidator();
        LOGGER.debug("必选资源包校验器初始化完成");
    }

    private void initFileTransferServer() {
        fileTransferServer = new FileTransferServer(crlConfig);
        LOGGER.debug("文件传输服务初始化完成");
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            CrlCommand.register(dispatcher);
        });
        LOGGER.debug("CRL命令注册完成");
    }

    private void registerNetworkChannels() {
        Identifier requestChannel = new Identifier(
                NetworkConstants.CHANNEL_NAMESPACE,
                NetworkConstants.CHANNEL_REQUEST_PACKS
        );
        ServerPlayNetworking.registerGlobalReceiver(requestChannel,
                (server, player, handler, buf, responseSender) ->
                        ServerPackSyncSender.sendPackList(player));

        Identifier readyChannel = new Identifier(
                NetworkConstants.CHANNEL_NAMESPACE,
                NetworkConstants.CHANNEL_PACK_READY
        );
        ServerPlayNetworking.registerGlobalReceiver(readyChannel,
                (server, player, handler, buf, responseSender) ->
                        packValidator.onPlayerPackReady(player, buf.readString()));

        // 注册文件传输请求处理器
        Identifier transferRequestChannel = new Identifier(
                NetworkConstants.CHANNEL_NAMESPACE,
                NetworkConstants.CHANNEL_REQUEST_FILE_TRANSFER
        );
        Identifier transferPortChannel = new Identifier(
                NetworkConstants.CHANNEL_NAMESPACE,
                NetworkConstants.CHANNEL_FILE_TRANSFER_PORT
        );
        ServerPlayNetworking.registerGlobalReceiver(transferRequestChannel,
                (server, player, handler, buf, responseSender) -> {
                    String packId = buf.readString();
                    String playerName = player.getName().getString();
                    LOGGER.info("收到玩家 {} 的文件传输请求: packId={}", playerName, packId);

                    // 在线程池中处理端口分配和传输任务创建
                    server.execute(() -> {
                        int port = fileTransferServer.requestDownload(packId);
                        if (port > 0) {
                            PacketByteBuf responseBuf = new PacketByteBuf(Unpooled.buffer());
                            responseBuf.writeString(packId);
                            responseBuf.writeInt(port);
                            ServerPlayNetworking.send(player, transferPortChannel, responseBuf);
                            LOGGER.info("已向玩家 {} 回复传输端口: packId={}, port={}", playerName, packId, port);
                        } else {
                            PacketByteBuf responseBuf = new PacketByteBuf(Unpooled.buffer());
                            responseBuf.writeString(packId);
                            responseBuf.writeInt(-1); // -1 表示失败
                            ServerPlayNetworking.send(player, transferPortChannel, responseBuf);
                            LOGGER.error("无法为玩家 {} 分配传输端口: packId={}", playerName, packId);
                        }
                    });
                });

        LOGGER.debug("服务端网络通信通道注册完成");
    }

    private void registerPlayerLifecycleEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            String playerName = handler.player.getName().getString();
            ServerPackSyncSender.sendPackList(handler.player);
            packValidator.startPlayerValidation(handler.player);
            LOGGER.debug("已向玩家 {} 下发资源包列表，启动必选包校验", playerName);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            packValidator.clearPlayerState(handler.player);
            LOGGER.debug("玩家 {} 断开连接，已清理资源包校验状态", handler.player.getName().getString());
        });

        LOGGER.debug("玩家生命周期事件监听注册完成");
    }

    public static CrlConfig getCrlConfig() {
        return crlConfig;
    }

    public static RequiredPackValidator getPackValidator() {
        return packValidator;
    }
}