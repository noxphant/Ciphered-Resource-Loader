package top.wyatt.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.wyatt.client.CipheredResourceLoaderClient;
import top.wyatt.client.pack.ClientPackManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CrlPackManagerScreen extends Screen {
    private final Screen parent;
    private List<String> localPacks;
    private int scrollOffset = 0;

    public CrlPackManagerScreen(Screen parent) {
        super(Text.literal("加密资源包管理"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ClientPackManager packManager = CipheredResourceLoaderClient.getClientPackManager();
        packManager.scanLocalEncryptedPacks();

        String[] files = packManager.getEncryptedPacksDir().toFile().list((dir, name) -> name.endsWith(".crp"));
        localPacks = files != null ? Arrays.asList(files) : List.of();

        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> {
            client.setScreen(parent);
        }).dimensions(width / 2 - 100, height - 30, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);

        context.drawTextWithShadow(textRenderer, title, width / 2 - textRenderer.getWidth(title) / 2, 20, 0xFFFFFF);

        context.drawTextWithShadow(textRenderer, Text.literal("本地加密资源包:"), 20, 40, 0xAAAAAA);

        int y = 60;
        int maxVisible = (height - 100) / 20;
        int start = Math.min(scrollOffset, Math.max(0, localPacks.size() - maxVisible));

        for (int i = start; i < Math.min(start + maxVisible, localPacks.size()); i++) {
            String packName = localPacks.get(i);
            context.drawTextWithShadow(textRenderer, Text.literal("§f" + packName), 20, y, 0xFFFFFF);
            y += 20;
        }

        if (localPacks.isEmpty()) {
            context.drawTextWithShadow(textRenderer, Text.literal("§c暂无本地加密资源包"), 20, 60, 0xFFFFFF);
        }

        context.drawTextWithShadow(textRenderer, Text.literal("加密资源包目录: " + CipheredResourceLoaderClient.getClientPackManager().getEncryptedPacksDir()),
                20, height - 60, 0x888888);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int maxOffset = Math.max(0, localPacks.size() - (height - 100) / 20);
        scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset - (int) amount));
        return true;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}