package top.wyatt.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.wyatt.client.CipheredResourceLoaderClient;
import top.wyatt.client.screen.DownloadManagerScreen;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    @Shadow
    protected MinecraftClient client;

    @Shadow
    protected <T extends Element & Drawable & Selectable> T addDrawableChild(T drawableElement) {
        return null;
    }

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addDownloadManagerButton(CallbackInfo ci) {
        try {
            int buttonWidth = 100;
            int buttonHeight = 20;
            int x = this.width - buttonWidth - 10;
            int y = 10;

            CipheredResourceLoaderClient.LOGGER.debug("正在主菜单添加下载管理按钮，位置: ({}, {})", x, y);

            ButtonWidget downloadButton = ButtonWidget.builder(
                            Text.literal("下载管理"),
                            button -> {
                                CipheredResourceLoaderClient.LOGGER.info("打开下载管理界面");
                                if (this.client != null) {
                                    this.client.setScreen(new DownloadManagerScreen(this));
                                }
                            })
                    .dimensions(x, y, buttonWidth, buttonHeight)
                    .build();

            this.addDrawableChild(downloadButton);
            CipheredResourceLoaderClient.LOGGER.debug("下载管理按钮添加成功");
        } catch (Exception e) {
            CipheredResourceLoaderClient.LOGGER.error("添加下载管理按钮失败", e);
        }
    }
}
