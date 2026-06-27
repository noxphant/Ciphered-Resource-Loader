package top.wyatt.client.net;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import top.wyatt.client.CipheredResourceLoaderClient;
import top.wyatt.client.download.DownloadRecord;
import top.wyatt.client.download.DownloadRecordManager;
import top.wyatt.client.pack.ClientPackManager;
import top.wyatt.client.pack.ServerPackInfo;
import top.wyatt.client.transfer.FileDownloadClient;
import top.wyatt.core.net.NetworkConstants;

import java.util.ArrayList;
import java.util.List;
import java.net.InetSocketAddress;
import java.nio.file.Path;

public class ClientPackSyncHandler {
    private static final Identifier SYNC_CHANNEL = new Identifier(
            NetworkConstants.CHANNEL_NAMESPACE,
            NetworkConstants.CHANNEL_SYNC_PACKS
    );

    private static final Identifier READY_CHANNEL = new Identifier(
            NetworkConstants.CHANNEL_NAMESPACE,
            NetworkConstants.CHANNEL_PACK_READY
    );

    private static final Identifier REQUEST_CHANNEL = new Identifier(
            NetworkConstants.CHANNEL_NAMESPACE,
            NetworkConstants.CHANNEL_REQUEST_PACKS
    );

    private static final Identifier FILE_TRANSFER_REQUEST_CHANNEL = new Identifier(
            NetworkConstants.CHANNEL_NAMESPACE,
            NetworkConstants.CHANNEL_REQUEST_FILE_TRANSFER
    );

    private static final Identifier FILE_TRANSFER_PORT_CHANNEL = new Identifier(
            NetworkConstants.CHANNEL_NAMESPACE,
            NetworkConstants.CHANNEL_FILE_TRANSFER_PORT
    );

    private static String currentServerAddress = "";

    public static void registerReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(SYNC_CHANNEL, (client, handler, buf, responseSender) -> {
            List<ServerPackInfo.PackEntry> packs = new ArrayList<>();
            int packCount = buf.readInt();

            for (int i = 0; i < packCount; i++) {
                String packId = buf.readString();
                String displayName = buf.readString();
                String fileName = buf.readString();
                String sha256 = buf.readString();
                long fileSize = buf.readLong();
                boolean encrypted = buf.readBoolean();
                boolean required = buf.readBoolean();
                String decryptKey = buf.readString();

                ClientPackManager packManager = CipheredResourceLoaderClient.getClientPackManager();
                boolean installed = packManager.isPackDownloaded(packId, sha256);

                packs.add(new ServerPackInfo.PackEntry(packId, displayName, fileName, sha256, fileSize,
                        encrypted, required, installed));
            }

            String serverAddress = handler.getConnection().getAddress().toString();
            currentServerAddress = serverAddress;
            ServerPackInfo.createOrUpdate(serverAddress, packs);

            client.execute(() -> handleRequiredPacks(packs));
        });

        // 注册文件传输端口回复处理器
        ClientPlayNetworking.registerGlobalReceiver(FILE_TRANSFER_PORT_CHANNEL,
                (client, handler, buf, responseSender) -> {
                    String packId = buf.readString();
                    int port = buf.readInt();

                    client.execute(() -> {
                        if (port <= 0) {
                            CipheredResourceLoaderClient.LOGGER.error("服务端无法分配传输端口: packId={}", packId);
                            ServerPackInfo packInfo = ServerPackInfo.getForServer(currentServerAddress);
                            if (packInfo != null) {
                                packInfo.setPackError(packId, "服务端无法分配传输端口");
                            }
                            return;
                        }

                        // 获取服务器地址
                        String serverHost = ((InetSocketAddress) handler.getConnection().getAddress())
                                .getAddress().getHostAddress();
                        startTcpDownload(packId, serverHost, port);
                    });
                });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            currentServerAddress = handler.getConnection().getAddress().toString();
            CipheredResourceLoaderClient.LOGGER.debug("已连接到服务器: {}", currentServerAddress);
            requestPackList();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ServerPackInfo.clearForServer(currentServerAddress);
            CipheredResourceLoaderClient.getClientPackManager().clearServerPackStates();
            currentServerAddress = "";
            CipheredResourceLoaderClient.LOGGER.debug("已断开服务器连接");
        });
    }

    private static void requestPackList() {
        if (!canSendPackets()) {
            CipheredResourceLoaderClient.LOGGER.warn("无法发送资源包列表请求：不在游戏中");
            return;
        }
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        ClientPlayNetworking.send(REQUEST_CHANNEL, buf);
        CipheredResourceLoaderClient.LOGGER.debug("已发送资源包列表请求");
    }

    private static void handleRequiredPacks(List<ServerPackInfo.PackEntry> packs) {
        List<ServerPackInfo.PackEntry> missingRequired = packs.stream()
                .filter(ServerPackInfo.PackEntry::required)
                .filter(p -> !p.installed())
                .toList();

        if (!missingRequired.isEmpty()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(
                        Text.literal("§c检测到缺少必选资源包，请下载后重新连接"),
                        false
                );
            }

            for (ServerPackInfo.PackEntry pack : missingRequired) {
                downloadPack(pack);
            }
        } else {
            sendPackReady("all_required");
        }
    }

    public static void downloadPack(ServerPackInfo.PackEntry pack) {
        final String packId = pack.packId();
        final String fileName = pack.fileName();
        final String sha256 = pack.sha256();
        final long fileSize = pack.fileSize();
        final boolean encrypted = pack.encrypted();
        final String packDisplayName = pack.displayName();
        final String serverAddress = currentServerAddress;

        ClientPackManager packManager = CipheredResourceLoaderClient.getClientPackManager();

        ServerPackInfo packInfo = ServerPackInfo.getForServer(currentServerAddress);
        if (packInfo != null) {
            packInfo.updateDownloadProgress(packId, 0, true);
        }

        // 向服务端请求文件传输端口
        if (!canSendPackets()) {
            CipheredResourceLoaderClient.LOGGER.warn("无法发送文件传输请求 {}：不在游戏中", packId);
            if (packInfo != null) {
                packInfo.setPackError(packId, "不在游戏中");
            }
            return;
        }

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(packId);
        ClientPlayNetworking.send(FILE_TRANSFER_REQUEST_CHANNEL, buf);
        CipheredResourceLoaderClient.LOGGER.info("已向服务端请求文件传输: packId={}", packId);
    }

    /**
     * 通过 TCP 从服务端下载文件并校验哈希。
     */
    private static void startTcpDownload(String packId, String serverHost, int port) {
        final String serverAddress = currentServerAddress;
        ServerPackInfo packInfo = ServerPackInfo.getForServer(serverAddress);
        if (packInfo == null) {
            CipheredResourceLoaderClient.LOGGER.warn("无法找到服务器资源包信息: {}", serverAddress);
            return;
        }

        ServerPackInfo.PackEntry pack = packInfo.getPackById(packId);
        if (pack == null) {
            CipheredResourceLoaderClient.LOGGER.warn("无法找到资源包 {} 的信息", packId);
            return;
        }

        final String fileName = pack.fileName();
        final String sha256 = pack.sha256();
        final long fileSize = pack.fileSize();
        final boolean encrypted = pack.encrypted();
        final String packDisplayName = pack.displayName();
        final long startTime = System.currentTimeMillis();

        ClientPackManager packManager = CipheredResourceLoaderClient.getClientPackManager();
        Path savePath = packManager.getDownloadPath(fileName, encrypted);

        CipheredResourceLoaderClient.LOGGER.info("开始TCP下载: packId={}, host={}, port={}, file={}, size={}",
                packId, serverHost, port, fileName, fileSize);

        FileDownloadClient.downloadAsync(serverHost, port, packId, fileName, sha256, fileSize, savePath,
                (percent, downloaded, total) -> {
                    // 在渲染线程更新进度
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client != null) {
                        client.execute(() -> {
                            ServerPackInfo currentPackInfo = ServerPackInfo.getForServer(serverAddress);
                            if (currentPackInfo != null) {
                                currentPackInfo.updateDownloadProgress(packId, percent, true);
                            }
                        });
                    }
                })
                .thenAccept(result -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client == null) {
                        CipheredResourceLoaderClient.LOGGER.warn("MinecraftClient 实例不可用，无法更新下载状态");
                        return;
                    }
                    client.execute(() -> {
                        try {
                            ServerPackInfo currentPackInfo = ServerPackInfo.getForServer(serverAddress);
                            long endTime = System.currentTimeMillis();

                            if (result.success()) {
                                // 标记为已安装
                                packManager.markPackDownloaded(packId, savePath);
                                if (currentPackInfo != null) {
                                    currentPackInfo.updatePackInstalled(packId, true);
                                }
                                DownloadRecord record = new DownloadRecord(
                                        packId, packDisplayName, serverAddress,
                                        result.fileSize(), startTime, endTime, true, null);
                                DownloadRecordManager.getInstance().addRecord(record);

                                // 发送就绪消息
                                if (canSendPackets()) {
                                    sendPackReady(packId);
                                }
                                CipheredResourceLoaderClient.LOGGER.info("资源包 {} 下载完成，哈希校验通过", packId);
                            } else {
                                if (currentPackInfo != null) {
                                    currentPackInfo.setPackError(packId, result.errorMessage());
                                }
                                DownloadRecord record = new DownloadRecord(
                                        packId, packDisplayName, serverAddress,
                                        0, startTime, endTime, false, result.errorMessage());
                                DownloadRecordManager.getInstance().addRecord(record);

                                CipheredResourceLoaderClient.LOGGER.error("资源包 {} 下载失败: {}", packId, result.errorMessage());
                            }
                        } catch (Exception e) {
                            CipheredResourceLoaderClient.LOGGER.error("更新下载状态时发生异常: packId={}", packId, e);
                        }
                    });
                });
    }

    public static void sendPackReady(String packId) {
        if (!canSendPackets()) {
            CipheredResourceLoaderClient.LOGGER.warn("无法发送资源包就绪消息 {}：不在游戏中", packId);
            return;
        }
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(packId);
        ClientPlayNetworking.send(READY_CHANNEL, buf);
        CipheredResourceLoaderClient.LOGGER.debug("已发送资源包就绪消息: {}", packId);
    }

    private static boolean canSendPackets() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null && client.getNetworkHandler() != null
                && ClientPlayNetworking.canSend(READY_CHANNEL);
    }
}