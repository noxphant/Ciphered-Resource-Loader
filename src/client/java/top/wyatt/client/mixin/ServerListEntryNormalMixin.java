package top.wyatt.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.wyatt.client.CipheredResourceLoaderClient;
import top.wyatt.client.config.ClientUiConfig;
import top.wyatt.client.pack.ServerPackInfo;

@Mixin(MultiplayerServerListWidget.ServerEntry.class)
public abstract class ServerListEntryNormalMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void renderCrlPackInfo(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci) {
        try {
            if (!ClientUiConfig.getInstance().isShowServerListIndicator()) {
                return;
            }

            MultiplayerServerListWidget.ServerEntry entry = (MultiplayerServerListWidget.ServerEntry) (Object) this;
            ServerInfo serverInfo = entry.getServer();
            if (serverInfo == null) return;

            ServerPackInfo packInfo = ServerPackInfo.getForServer(serverInfo.address);
            if (packInfo == null || packInfo.getPackCount() == 0) {
                return;
            }

            String statusText = packInfo.getOverallStatusText();
            int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(statusText) + 10;
            int infoX = x + entryWidth - textWidth - 5;
            int infoY = y + 2;

            int bgColor = 0x90000000;
            context.fill(infoX, infoY, infoX + textWidth, infoY + 12, bgColor);

            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    Text.literal(statusText),
                    infoX + 5, infoY + 2, 0xFFFFFF);

            int tooltipX = infoX;
            int tooltipY = infoY + 12;
            if (hovered && mouseX >= tooltipX && mouseX <= tooltipX + textWidth && mouseY >= infoY && mouseY <= infoY + 12) {
                context.drawTooltip(MinecraftClient.getInstance().textRenderer,
                        packInfo.getTooltipText(), mouseX, mouseY);
            }
        } catch (Exception e) {
            CipheredResourceLoaderClient.LOGGER.error("渲染服务器列表CRL信息失败", e);
        }
    }
}
