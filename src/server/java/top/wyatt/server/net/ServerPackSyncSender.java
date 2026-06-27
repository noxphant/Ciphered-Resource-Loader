package top.wyatt.server.net;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import top.wyatt.CipheredResourceLoader;
import top.wyatt.core.net.NetworkConstants;
import top.wyatt.server.config.CrlConfig;
import top.wyatt.server.config.CrlPackEntry;

import java.util.List;

public class ServerPackSyncSender {
    private static final Identifier CHANNEL = new Identifier(
            NetworkConstants.CHANNEL_NAMESPACE,
            NetworkConstants.CHANNEL_SYNC_PACKS
    );

    public static void sendPackList(ServerPlayerEntity player) {
        CrlConfig config = CipheredResourceLoader.getCrlConfig();
        List<CrlPackEntry> packs = config.getAllPacks();

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(packs.size());

        for (CrlPackEntry pack : packs) {
            buf.writeString(pack.getPackId());
            buf.writeString(pack.getDisplayName() != null ? pack.getDisplayName() : "");
            buf.writeString(pack.getFileName() != null ? pack.getFileName() : "");
            buf.writeString(pack.getSha256() != null ? pack.getSha256() : "");
            buf.writeLong(pack.getFileSize());
            buf.writeBoolean(pack.isEncrypted());
            buf.writeBoolean(pack.isRequired());
            buf.writeString(pack.getDecryptKey() != null ? pack.getDecryptKey() : "");
        }

        ServerPlayNetworking.send(player, CHANNEL, buf);
    }
}