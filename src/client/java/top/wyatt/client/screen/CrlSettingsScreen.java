package top.wyatt.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.wyatt.client.CipheredResourceLoaderClient;
import top.wyatt.client.config.ClientUiConfig;

public class CrlSettingsScreen extends Screen {
    private final Screen parent;
    private ButtonWidget showPackScreenButtonBtn;
    private ButtonWidget showServerListIndicatorBtn;
    private ButtonWidget showDownloadProgressBtn;
    private ButtonWidget showStatusMessagesBtn;

    public CrlSettingsScreen(Screen parent) {
        super(Text.literal("CRL 设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ClientUiConfig config = ClientUiConfig.getInstance();

        int centerX = this.width / 2;
        int startY = 40;
        int spacing = 28;
        int buttonWidth = 240;
        int buttonHeight = 20;

        showPackScreenButtonBtn = ButtonWidget.builder(
                        getToggleText("资源包页面显示管理按钮", config.isShowPackScreenButton()),
                        button -> {
                            boolean newVal = !config.isShowPackScreenButton();
                            config.setShowPackScreenButton(newVal);
                            button.setMessage(getToggleText("资源包页面显示管理按钮", newVal));
                        })
                .dimensions(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight)
                .build();
        this.addDrawableChild(showPackScreenButtonBtn);

        showServerListIndicatorBtn = ButtonWidget.builder(
                        getToggleText("多人游戏列表显示资源包标识", config.isShowServerListIndicator()),
                        button -> {
                            boolean newVal = !config.isShowServerListIndicator();
                            config.setShowServerListIndicator(newVal);
                            button.setMessage(getToggleText("多人游戏列表显示资源包标识", newVal));
                        })
                .dimensions(centerX - buttonWidth / 2, startY + spacing, buttonWidth, buttonHeight)
                .build();
        this.addDrawableChild(showServerListIndicatorBtn);

        showDownloadProgressBtn = ButtonWidget.builder(
                        getToggleText("显示下载进度提示", config.isShowDownloadProgress()),
                        button -> {
                            boolean newVal = !config.isShowDownloadProgress();
                            config.setShowDownloadProgress(newVal);
                            button.setMessage(getToggleText("显示下载进度提示", newVal));
                        })
                .dimensions(centerX - buttonWidth / 2, startY + spacing * 2, buttonWidth, buttonHeight)
                .build();
        this.addDrawableChild(showDownloadProgressBtn);

        showStatusMessagesBtn = ButtonWidget.builder(
                        getToggleText("显示状态消息", config.isShowStatusMessages()),
                        button -> {
                            boolean newVal = !config.isShowStatusMessages();
                            config.setShowStatusMessages(newVal);
                            button.setMessage(getToggleText("显示状态消息", newVal));
                        })
                .dimensions(centerX - buttonWidth / 2, startY + spacing * 3, buttonWidth, buttonHeight)
                .build();
        this.addDrawableChild(showStatusMessagesBtn);

        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("保存并返回"),
                        button -> {
                            config.save();
                            CipheredResourceLoaderClient.LOGGER.info("CRL设置已保存");
                            this.client.setScreen(parent);
                        })
                .dimensions(centerX - 100, this.height - 40, 200, 20)
                .build());
    }

    private Text getToggleText(String label, boolean enabled) {
        String status = enabled ? "§a[启用]" : "§c[禁用]";
        return Text.literal(status + " §r" + label);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
