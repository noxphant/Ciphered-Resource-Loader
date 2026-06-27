package top.wyatt.client.mixin;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.wyatt.client.CipheredResourceLoaderClient;
import top.wyatt.client.pack.ClientPackManager;
import top.wyatt.client.pack.CipheredResourcePack;

import java.nio.file.Path;
import java.util.List;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerLoaderMixin {

    @Inject(method = "setupServer", at = @At("TAIL"))
    private void onIntegratedServerSetup(CallbackInfo ci) {
        loadLocalEncryptedPacks();
    }

    private void loadLocalEncryptedPacks() {
        ClientPackManager packManager = CipheredResourceLoaderClient.getClientPackManager();
        packManager.scanLocalEncryptedPacks();

        java.io.File[] files = packManager.getEncryptedPacksDir().toFile().listFiles((dir, name) -> name.endsWith(".crp"));
        List<Path> encryptedPacks = files != null ?
                java.util.Arrays.stream(files).map(java.io.File::toPath).collect(java.util.stream.Collectors.toList()) :
                List.of();

        CipheredResourceLoaderClient.LOGGER.info("单人游戏模式：发现 {} 个本地加密资源包", encryptedPacks.size());

        for (Path packPath : encryptedPacks) {
            String packId = packPath.getFileName().toString().replace(".crp", "");
            try {
                CipheredResourcePack pack = new CipheredResourcePack(
                        packPath,
                        packId,
                        packId,
                        new byte[32]
                );
                CipheredResourceLoaderClient.LOGGER.info("加载加密资源包: {}", packId);
            } catch (Exception e) {
                CipheredResourceLoaderClient.LOGGER.error("加载加密资源包失败: {}", packId, e);
            }
        }
    }
}