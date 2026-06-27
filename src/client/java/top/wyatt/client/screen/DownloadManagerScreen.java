package top.wyatt.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.wyatt.client.download.DownloadRecord;
import top.wyatt.client.download.DownloadRecordManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class DownloadManagerScreen extends Screen {
    private final Screen parent;
    private int scrollOffset = 0;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private ButtonWidget refreshButton;
    private ButtonWidget clearButton;

    public DownloadManagerScreen(Screen parent) {
        super(Text.literal("下载管理"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        refreshButton = ButtonWidget.builder(
                        Text.literal("刷新"),
                        button -> {
                            DownloadRecordManager.getInstance().refresh();
                        })
                .dimensions(this.width - 220, 6, 80, 20)
                .build();
        this.addDrawableChild(refreshButton);

        clearButton = ButtonWidget.builder(
                        Text.literal("清除记录"),
                        button -> {
                            DownloadRecordManager.getInstance().clearRecords();
                        })
                .dimensions(this.width - 130, 6, 120, 20)
                .build();
        this.addDrawableChild(clearButton);

        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("返回"),
                        button -> this.client.setScreen(parent))
                .dimensions(this.width / 2 - 100, this.height - 30, 200, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFF);

        List<DownloadRecord> records = DownloadRecordManager.getInstance().getRecords();
        int headerY = 32;

        context.fill(10, headerY - 2, this.width - 10, headerY + 14, 0x60000000);
        context.drawTextWithShadow(this.textRenderer, Text.literal("§l资源包"), 15, headerY, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("§l来源服务器"), this.width / 4 + 10, headerY, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("§l大小"), this.width / 2 - 40, headerY, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("§l耗时"), this.width / 2 + 60, headerY, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("§l完成时间"), this.width - 150, headerY, 0xFFFFFF);

        int recordY = headerY + 20;
        int recordHeight = 24;
        int visibleCount = (this.height - 80) / recordHeight;
        int startIndex = Math.min(scrollOffset, Math.max(0, records.size() - visibleCount));

        for (int i = startIndex; i < Math.min(startIndex + visibleCount, records.size()); i++) {
            DownloadRecord record = records.get(i);
            int y = recordY + (i - startIndex) * recordHeight;

            int bgColor = (i % 2 == 0) ? 0x40000000 : 0x20000000;
            context.fill(10, y, this.width - 10, y + recordHeight - 2, bgColor);

            String statusColor = record.isSuccess() ? "§a" : "§c";
            String packName = record.getPackName() != null && !record.getPackName().isEmpty()
                    ? record.getPackName() : record.getPackId();
            if (packName.length() > 25) {
                packName = packName.substring(0, 23) + "...";
            }
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal(statusColor + packName),
                    15, y + 6, 0xFFFFFF);

            String server = record.getServerAddress() != null ? record.getServerAddress() : "未知";
            if (server.length() > 20) {
                server = server.substring(0, 18) + "...";
            }
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("§7" + server),
                    this.width / 4 + 10, y + 6, 0xFFFFFF);

            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("§f" + record.getHumanReadableSize()),
                    this.width / 2 - 40, y + 6, 0xFFFFFF);

            String duration = record.isSuccess() ? record.getHumanReadableDuration() : "-";
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("§e" + duration),
                    this.width / 2 + 60, y + 6, 0xFFFFFF);

            String endTime = record.getDownloadEndTime() > 0
                    ? dateFormat.format(new Date(record.getDownloadEndTime()))
                    : "-";
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("§7" + endTime),
                    this.width - 150, y + 6, 0xFFFFFF);

            if (!record.isSuccess() && record.getErrorMessage() != null) {
                context.drawTextWithShadow(this.textRenderer,
                        Text.literal("§c" + record.getErrorMessage()),
                        15, y + 16, 0xFFFFFF);
            }
        }

        if (records.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("§7暂无下载记录"),
                    this.width / 2, this.height / 2, 0xFFFFFF);
        }

        String statusText = "共 " + records.size() + " 条记录";
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("§7" + statusText),
                15, this.height - 50, 0xFFFFFF);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        List<DownloadRecord> records = DownloadRecordManager.getInstance().getRecords();
        int recordHeight = 24;
        int visibleCount = (this.height - 80) / recordHeight;
        int maxOffset = Math.max(0, records.size() - visibleCount);
        scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset - (int) amount));
        return true;
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
