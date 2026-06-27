package top.wyatt.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.wyatt.client.CipheredResourceLoaderClient;
import top.wyatt.client.net.ClientPackSyncHandler;
import top.wyatt.client.pack.ClientPackManager;
import top.wyatt.client.pack.ServerPackInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 资源包下载页面 —— 支持多选、全选、进度条、实时状态、Toast 弹窗。
 */
public class PackDownloadScreen extends Screen {
    private final Screen parent;

    // 滚动
    private int scrollOffset = 0;
    private static final int ROW_HEIGHT = 28;
    private static final int HEADER_Y = 50;
    private static final int LIST_TOP = HEADER_Y + 20;

    // 选中项
    private final Set<String> selectedPackIds = new LinkedHashSet<>();

    // Toast 通知
    private final Deque<ToastMessage> toasts = new ArrayDeque<>();
    private static final int TOAST_MAX = 5;
    private static final int TOAST_WIDTH = 200;
    private static final int TOAST_HEIGHT = 32;
    private static final long TOAST_DURATION_MS = 5_000;

    public PackDownloadScreen(Screen parent) {
        super(Text.translatable("ciphered-resource-loader.screen.download.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // 全选按钮
        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("ciphered-resource-loader.button.select_all"),
                        button -> selectAll(true))
                .dimensions(this.width / 2 - 210, 22, 60, 20)
                .build());

        // 取消全选按钮
        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("ciphered-resource-loader.button.deselect_all"),
                        button -> selectAll(false))
                .dimensions(this.width / 2 - 145, 22, 72, 20)
                .build());

        // 下载选中
        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("ciphered-resource-loader.button.download"),
                        button -> startSelectedDownloads())
                .dimensions(this.width / 2 - 68, 22, 80, 20)
                .build());

        // 删除已下载
        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("ciphered-resource-loader.button.delete"),
                        button -> deleteSelectedDownloaded())
                .dimensions(this.width / 2 + 17, 22, 80, 20)
                .build());

        // 刷新
        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("ciphered-resource-loader.button.refresh"),
                        button -> { /* 重新渲染即可 */ })
                .dimensions(this.width / 2 + 102, 22, 50, 20)
                .build());

        // 返回
        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("ciphered-resource-loader.button.back"),
                        button -> {
                            if (this.client != null) this.client.setScreen(parent);
                        })
                .dimensions(this.width / 2 - 100, this.height - 30, 200, 20)
                .build());
    }

    // ==================== 按钮逻辑 ====================

    private void selectAll(boolean select) {
        ServerPackInfo info = ServerPackInfo.getCurrentServerPackInfo();
        if (info == null) return;
        if (select) {
            info.getPacks().forEach(p -> selectedPackIds.add(p.packId()));
        } else {
            selectedPackIds.clear();
        }
    }

    private void startSelectedDownloads() {
        if (selectedPackIds.isEmpty()) {
            addToast("§e" + Text.translatable("ciphered-resource-loader.toast.select_packs").getString());
            return;
        }
        ServerPackInfo info = ServerPackInfo.getCurrentServerPackInfo();
        if (info == null) {
            addToast("§c" + Text.translatable("ciphered-resource-loader.toast.no_server").getString());
            return;
        }
        int count = 0;
        for (String packId : selectedPackIds) {
            ServerPackInfo.PackEntry pack = info.getPackById(packId);
            if (pack != null && !pack.installed() && pack.downloadState() != ServerPackInfo.DownloadState.DOWNLOADING) {
                ClientPackSyncHandler.downloadPack(pack);
                count++;
            }
        }
        if (count > 0) {
            addToast("§a" + Text.translatable("ciphered-resource-loader.toast.download_started", count).getString());
        } else {
            addToast("§7" + Text.translatable("ciphered-resource-loader.toast.already_installed").getString());
        }
        selectedPackIds.clear();
    }

    private void deleteSelectedDownloaded() {
        if (selectedPackIds.isEmpty()) {
            addToast("§e" + Text.translatable("ciphered-resource-loader.toast.select_downloaded").getString());
            return;
        }
        ServerPackInfo info = ServerPackInfo.getCurrentServerPackInfo();
        if (info == null) return;

        ClientPackManager packManager = CipheredResourceLoaderClient.getClientPackManager();
        int deleted = 0;
        for (String packId : selectedPackIds) {
            ServerPackInfo.PackEntry pack = info.getPackById(packId);
            if (pack == null || !pack.installed()) continue;

            Path filePath = packManager.getDownloadPath(pack.fileName(), pack.encrypted());
            try {
                if (Files.deleteIfExists(filePath)) {
                    info.updatePackInstalled(packId, false);
                    deleted++;
                    CipheredResourceLoaderClient.LOGGER.info("已删除资源包文件: {}", filePath);
                }
            } catch (IOException e) {
                CipheredResourceLoaderClient.LOGGER.error("删除资源包文件失败: {}", filePath, e);
            }
        }
        if (deleted > 0) {
            addToast("§a" + Text.translatable("ciphered-resource-loader.toast.deleted", deleted).getString());
        } else {
            addToast("§7" + Text.translatable("ciphered-resource-loader.toast.no_files_delete").getString());
        }
        selectedPackIds.clear();
    }

    // ==================== Toast ====================

    private void addToast(String message) {
        toasts.addFirst(new ToastMessage(message, System.currentTimeMillis()));
        while (toasts.size() > TOAST_MAX) {
            toasts.removeLast();
        }
    }

    // ==================== 渲染 ====================

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);

        // 标题
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);

        // 表头
        int headerY = HEADER_Y;
        context.fill(10, headerY - 2, this.width - 10, headerY + 14, 0x60000000);
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("ciphered-resource-loader.screen.download.header.pack"),
                30, headerY, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("ciphered-resource-loader.screen.download.header.type"),
                this.width / 5 + 50, headerY, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("ciphered-resource-loader.screen.download.header.status"),
                this.width / 2 - 10, headerY, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("ciphered-resource-loader.screen.download.header.size"),
                this.width / 2 + 100, headerY, 0xFFFFFF);

        ServerPackInfo packInfo = ServerPackInfo.getCurrentServerPackInfo();
        int visibleCount = (this.height - 120) / ROW_HEIGHT;

        if (packInfo == null) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("ciphered-resource-loader.screen.download.no_server"),
                    this.width / 2, this.height / 2, 0xFFFFFF);
        } else {
            List<ServerPackInfo.PackEntry> packs = packInfo.getPacks();
            int maxOffset = Math.max(0, packs.size() - visibleCount);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxOffset));

            int startIndex = Math.min(scrollOffset, Math.max(0, packs.size() - visibleCount));

            for (int i = startIndex; i < Math.min(startIndex + visibleCount, packs.size()); i++) {
                ServerPackInfo.PackEntry pack = packs.get(i);
                int y = LIST_TOP + (i - startIndex) * ROW_HEIGHT;
                renderPackRow(context, pack, y, mouseX, mouseY);
            }

            // 底部统计
            int total = packs.size();
            int installed = (int) packs.stream().filter(ServerPackInfo.PackEntry::installed).count();
            int downloading = (int) packs.stream().filter(p -> p.downloadState() == ServerPackInfo.DownloadState.DOWNLOADING).count();
            String stats = Text.translatable("ciphered-resource-loader.stats", total, installed, downloading,
                    selectedPackIds.size()).getString();
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7" + stats), 15, this.height - 50, 0xFFFFFF);
        }

        // 渲染 Toast
        renderToasts(context);
    }

    private void renderPackRow(DrawContext context, ServerPackInfo.PackEntry pack, int y, int mouseX, int mouseY) {
        // Checkbox
        boolean selected = selectedPackIds.contains(pack.packId());
        int checkboxX = 14;
        int checkboxY = y + 6;
        int checkboxSize = 12;

        context.fill(checkboxX, checkboxY, checkboxX + checkboxSize, checkboxY + checkboxSize,
                selected ? 0xFF55FF55 : 0xFF555555);
        context.fill(checkboxX + 1, checkboxY + 1, checkboxX + checkboxSize - 1, checkboxY + checkboxSize - 1,
                selected ? 0xFF00AA00 : 0xFF333333);
        if (selected) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("✓"),
                    checkboxX + 2, checkboxY, 0xFFFFFF);
        }

        // 名称 — 剥离 Minecraft 格式码，避免版本号显示为黑色
        String name = pack.displayName() != null && !pack.displayName().isEmpty()
                ? pack.displayName() : pack.packId();
        // 移除 § + 单字符 格式码，保留纯文本
        String cleanName = name.replaceAll("§[0-9a-fklmnor]", "");
        int nameMaxWidth = this.width / 5 - 10;
        String displayName = this.textRenderer.trimToWidth(cleanName, nameMaxWidth);
        if (!displayName.equals(cleanName)) displayName = displayName + "...";
        context.drawTextWithShadow(this.textRenderer, Text.literal("§f" + displayName),
                30, y + 6, 0xFFFFFF);

        // 类型
        String type = pack.required()
                ? "§c" + Text.translatable("ciphered-resource-loader.status.required").getString()
                : "§7" + Text.translatable("ciphered-resource-loader.status.optional").getString();
        context.drawTextWithShadow(this.textRenderer, Text.literal(type),
                this.width / 5 + 50, y + 6, 0xFFFFFF);

        // 状态 + 进度条
        int statusX = this.width / 2 - 10;
        int progressBarX = statusX + 55;
        int progressBarWidth = 60;
        int progressBarHeight = 10;

        switch (pack.downloadState()) {
            case DOWNLOADING -> {
                context.drawTextWithShadow(this.textRenderer,
                        Text.translatable("ciphered-resource-loader.status.downloading"),
                        statusX, y + 3, 0xFFAA00);
                int pct = pack.downloadPercent();
                // 进度条背景
                context.fill(progressBarX, y + 5, progressBarX + progressBarWidth, y + 5 + progressBarHeight, 0xFF333333);
                // 进度条填充
                int fillWidth = pct * progressBarWidth / 100;
                context.fill(progressBarX, y + 5, progressBarX + fillWidth, y + 5 + progressBarHeight, 0xFF00AA00);
                // 百分比
                int pctX = progressBarX + progressBarWidth + 4;
                context.drawTextWithShadow(this.textRenderer, Text.literal(pct + "%"),
                        pctX, y + 3, 0xFFFFFF);
                // 文件大小 — 放在百分比后面
                String sizeStr = formatFileSize(pack.fileSize());
                int sizeX = pctX + this.textRenderer.getWidth(pct + "%") + 8;
                context.drawTextWithShadow(this.textRenderer, Text.literal("§7" + sizeStr),
                        sizeX, y + 3, 0xFFFFFF);
            }
            case ERROR -> {
                context.drawTextWithShadow(this.textRenderer,
                        Text.translatable("ciphered-resource-loader.status.failed"),
                        statusX, y + 6, 0xFF5555);
                String errMsg = pack.errorMessage();
                if (errMsg != null && !errMsg.isEmpty()) {
                    String shortErr = errMsg.length() > 30 ? errMsg.substring(0, 28) + "..." : errMsg;
                    context.drawTextWithShadow(this.textRenderer, Text.literal("§4" + shortErr),
                            progressBarX, y + 6, 0xFFFFFF);
                }
                // 错误时文件大小也正常显示
                String sizeStr = formatFileSize(pack.fileSize());
                context.drawTextWithShadow(this.textRenderer, Text.literal("§7" + sizeStr),
                        this.width / 2 + 100, y + 6, 0xFFFFFF);
            }
            case NOT_DOWNLOADING -> {
                if (pack.installed()) {
                    context.drawTextWithShadow(this.textRenderer,
                            Text.translatable("ciphered-resource-loader.status.installed"),
                            statusX, y + 6, 0x55FF55);
                } else {
                    context.drawTextWithShadow(this.textRenderer,
                            Text.translatable("ciphered-resource-loader.status.not_installed"),
                            statusX, y + 6, 0xAAAAAA);
                }
                // 文件大小
                String sizeStr = formatFileSize(pack.fileSize());
                context.drawTextWithShadow(this.textRenderer, Text.literal("§7" + sizeStr),
                        this.width / 2 + 100, y + 6, 0xFFFFFF);
            }
        }
    }

    private void renderToasts(DrawContext context) {
        long now = System.currentTimeMillis();
        toasts.removeIf(t -> now - t.time > TOAST_DURATION_MS);

        int toastX = this.width - TOAST_WIDTH - 10;
        int baseY = this.height - 10 - TOAST_HEIGHT;
        int index = 0;
        for (ToastMessage toast : toasts) {
            int toastY = baseY - index * (TOAST_HEIGHT + 4);
            // 背景
            context.fill(toastX, toastY, toastX + TOAST_WIDTH, toastY + TOAST_HEIGHT, 0xCC000000);
            context.fill(toastX, toastY, toastX + 3, toastY + TOAST_HEIGHT, 0xFF00AA00);
            // 文字
            context.drawTextWithShadow(this.textRenderer, Text.literal(toast.message),
                    toastX + 8, toastY + 10, 0xFFFFFF);
            index++;
        }
    }

    // ==================== 交互 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            ServerPackInfo packInfo = ServerPackInfo.getCurrentServerPackInfo();
            if (packInfo != null) {
                List<ServerPackInfo.PackEntry> packs = packInfo.getPacks();
                int visibleCount = (this.height - 120) / ROW_HEIGHT;
                int startIndex = Math.min(scrollOffset, Math.max(0, packs.size() - visibleCount));

                for (int i = startIndex; i < Math.min(startIndex + visibleCount, packs.size()); i++) {
                    ServerPackInfo.PackEntry pack = packs.get(i);
                    int y = LIST_TOP + (i - startIndex) * ROW_HEIGHT;
                    int checkboxX = 14;
                    int checkboxY = y + 6;
                    int checkboxSize = 12;

                    if (mouseX >= checkboxX && mouseX <= checkboxX + checkboxSize
                            && mouseY >= checkboxY && mouseY <= checkboxY + checkboxSize) {
                        if (selectedPackIds.contains(pack.packId())) {
                            selectedPackIds.remove(pack.packId());
                        } else {
                            selectedPackIds.add(pack.packId());
                        }
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        ServerPackInfo packInfo = ServerPackInfo.getCurrentServerPackInfo();
        if (packInfo != null) {
            List<ServerPackInfo.PackEntry> packs = packInfo.getPacks();
            int visibleCount = (this.height - 120) / ROW_HEIGHT;
            int maxOffset = Math.max(0, packs.size() - visibleCount);
            scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset - (int) amount));
        }
        return true;
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }

    // ==================== 工具 ====================

    private static String formatFileSize(long bytes) {
        if (bytes <= 0) return "?";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static class ToastMessage {
        final String message;
        final long time;

        ToastMessage(String message, long time) {
            this.message = message;
            this.time = time;
        }
    }
}