package top.wyatt.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.wyatt.client.CipheredResourceLoaderClient;
import top.wyatt.client.config.ClientUiConfig;
import top.wyatt.client.screen.CrlPackManagerScreen;

@Mixin(PackScreen.class)
public abstract class ResourcePackOptionsScreenMixin {

    @Shadow
    protected MinecraftClient client;

    @Shadow
    protected <T extends Element & Drawable & Selectable> T addDrawableChild(T drawableElement) {
        return null;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addCrlManagerButton(CallbackInfo ci) {
        try {
            if (!ClientUiConfig.getInstance().isShowPackScreenButton()) {
                CipheredResourceLoaderClient.LOGGER.debug("资源包页面按钮已在配置中禁用，跳过添加");
                return;
            }

            PackScreen screen = (PackScreen) (Object) this;
            int buttonWidth = 120;
            int buttonHeight = 20;
            int x = screen.width - buttonWidth - 10;
            int y = 10;

            CipheredResourceLoaderClient.LOGGER.debug("正在添加加密资源包管理按钮，屏幕尺寸: {}x{}, 按钮位置: ({}, {})",
                    screen.width, screen.height, x, y);

            ButtonWidget crlButton = ButtonWidget.builder(Text.literal("加密资源包管理"), button -> {
                CipheredResourceLoaderClient.LOGGER.info("打开加密资源包管理页面");
                if (this.client != null) {
                    this.client.setScreen(new CrlPackManagerScreen(screen));
                }
            }).dimensions(x, y, buttonWidth, buttonHeight).build();

            this.addDrawableChild(crlButton);
            CipheredResourceLoaderClient.LOGGER.debug("加密资源包管理按钮添加成功");
        } catch (Exception e) {
            CipheredResourceLoaderClient.LOGGER.error("添加加密资源包管理按钮失败", e);
        }
    }
}