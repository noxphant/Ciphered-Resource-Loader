package top.wyatt.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.wyatt.client.CipheredResourceLoaderClient;
import top.wyatt.client.config.ClientUiConfig;
import top.wyatt.client.pack.ServerPackInfo;

import java.util.List;

@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixin {

    @Shadow
    private ServerInfo selectedEntry;

    @Inject(method = "render", at = @At("TAIL"))
    private void renderCrlPackDetails(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            if (!ClientUiConfig.getInstance().isShowServerListIndicator()) {
                return;
            }

            if (selectedEntry == null) return;

            ServerPackInfo packInfo = ServerPackInfo.getForServer(selectedEntry.address);
            if (packInfo == null || packInfo.getPackCount() == 0) {
                return;
            }

            MultiplayerScreen screen = (MultiplayerScreen) (Object) this;
            int panelX = screen.width - 200;
            int panelY = 30;
            int panelWidth = 190;

            int y = panelY;

            context.fill(panelX, y, panelX + panelWidth, y + 12, 0x90000000);
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    Text.literal("§l§a加密资源包信息"),
                    panelX + 5, y + 2, 0xFFFFFF);
            y += 16;

            int total = packInfo.getPackCount();
            int required = packInfo.getRequiredCount();
            int optional = packInfo.getOptionalCount();
            int downloading = packInfo.getDownloadingCount();
            int errors = packInfo.getErrorCount();

            context.fill(panelX, y, panelX + panelWidth, y + 10, 0x60000000);
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    Text.literal("§e总计: " + total + " (必选: " + required + " 选装: " + optional + ")"),
                    panelX + 5, y + 1, 0xAAAAAA);
            y += 14;

            if (downloading > 0) {
                context.fill(panelX, y, panelX + panelWidth, y + 10, 0x60666600);
                context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                        Text.literal("§6下载中: " + downloading + " 个"),
                        panelX + 5, y + 1, 0xFFFFFF);
                y += 14;
            }

            if (errors > 0) {
                context.fill(panelX, y, panelX + panelWidth, y + 10, 0x60660000);
                context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                        Text.literal("§c错误: " + errors + " 个"),
                        panelX + 5, y + 1, 0xFFFFFF);
                y += 14;
            }

            y += 4;
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    Text.literal("§7资源包列表:"),
                    panelX + 5, y, 0x888888);
            y += 12;

            List<ServerPackInfo.PackEntry> packs = packInfo.getPacks();
            for (ServerPackInfo.PackEntry pack : packs) {
                if (y + 10 > screen.height - 60) {
                    context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                            Text.literal("§7... 更多 ..."),
                            panelX + 5, y, 0x888888);
                    break;
                }

                String type = pack.required() ? "§c[必选]" : "§7[选装]";
                String status;

                switch (pack.downloadState()) {
                    case DOWNLOADING -> status = "§6" + pack.downloadPercent() + "%";
                    case ERROR -> status = "§c失败";
                    case NOT_DOWNLOADING -> {
                        if (pack.installed()) {
                            status = "§a已安装";
                        } else {
                            status = "§7未安装";
                        }
                    }
                    default -> status = "§7?";
                }

                String name = pack.displayName() != null && !pack.displayName().isEmpty()
                        ? pack.displayName() : pack.packId();
                if (name.length() > 20) {
                    name = name.substring(0, 18) + "...";
                }

                String line = type + " §f" + name;
                context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                        Text.literal(line),
                        panelX + 5, y, 0xFFFFFF);

                int statusWidth = MinecraftClient.getInstance().textRenderer.getWidth(status);
                context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                        Text.literal(status),
                        panelX + panelWidth - statusWidth - 5, y, 0xFFFFFF);

                y += 12;
            }
        } catch (Exception e) {
            CipheredResourceLoaderClient.LOGGER.error("渲染多人游戏屏幕CRL详情失败", e);
        }
    }
}
