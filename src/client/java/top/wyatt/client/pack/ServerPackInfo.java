package top.wyatt.client.pack;

import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ServerPackInfo {
    private static final Map<String, ServerPackInfo> serverPackInfoMap = new ConcurrentHashMap<>();

    private final List<PackEntry> packs = new ArrayList<>();
    private final String serverAddress;

    private ServerPackInfo(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public static ServerPackInfo getForServer(String address) {
        return serverPackInfoMap.get(address);
    }

    public static ServerPackInfo createOrUpdate(String address, List<PackEntry> packs) {
        ServerPackInfo info = serverPackInfoMap.computeIfAbsent(address, ServerPackInfo::new);
        info.packs.clear();
        info.packs.addAll(packs);
        return info;
    }

    public static void clearForServer(String address) {
        serverPackInfoMap.remove(address);
    }

    public static ServerPackInfo getCurrentServerPackInfo() {
        if (serverPackInfoMap.isEmpty()) return null;
        return serverPackInfoMap.values().iterator().next();
    }

    public List<PackEntry> getRequiredPacks() {
        return packs.stream().filter(PackEntry::required).collect(Collectors.toList());
    }

    public List<PackEntry> getOptionalPacks() {
        return packs.stream().filter(p -> !p.required()).collect(Collectors.toList());
    }

    public void updateDownloadProgress(String packId, int percent, boolean downloading) {
        for (int i = 0; i < packs.size(); i++) {
            PackEntry pack = packs.get(i);
            if (pack.packId().equals(packId)) {
                packs.set(i, new PackEntry(
                        pack.packId(),
                        pack.displayName(),
                        pack.fileName(),
                        pack.sha256(),
                        pack.fileSize(),
                        pack.encrypted(),
                        pack.required(),
                        pack.installed(),
                        downloading ? DownloadState.DOWNLOADING : DownloadState.NOT_DOWNLOADING,
                        percent,
                        pack.errorMessage()
                ));
                break;
            }
        }
    }

    public void updatePackInstalled(String packId, boolean installed) {
        for (int i = 0; i < packs.size(); i++) {
            PackEntry pack = packs.get(i);
            if (pack.packId().equals(packId)) {
                packs.set(i, new PackEntry(
                        pack.packId(),
                        pack.displayName(),
                        pack.fileName(),
                        pack.sha256(),
                        pack.fileSize(),
                        pack.encrypted(),
                        pack.required(),
                        installed,
                        DownloadState.NOT_DOWNLOADING,
                        0,
                        null
                ));
                break;
            }
        }
    }

    public void setPackError(String packId, String errorMessage) {
        for (int i = 0; i < packs.size(); i++) {
            PackEntry pack = packs.get(i);
            if (pack.packId().equals(packId)) {
                packs.set(i, new PackEntry(
                        pack.packId(),
                        pack.displayName(),
                        pack.fileName(),
                        pack.sha256(),
                        pack.fileSize(),
                        pack.encrypted(),
                        pack.required(),
                        pack.installed(),
                        DownloadState.ERROR,
                        0,
                        errorMessage
                ));
                break;
            }
        }
    }

    public int getPackCount() {
        return packs.size();
    }

    public int getRequiredCount() {
        return (int) packs.stream().filter(PackEntry::required).count();
    }

    public int getOptionalCount() {
        return (int) packs.stream().filter(p -> !p.required()).count();
    }

    public int getDownloadingCount() {
        return (int) packs.stream().filter(p -> p.downloadState() == DownloadState.DOWNLOADING).count();
    }

    public int getErrorCount() {
        return (int) packs.stream().filter(p -> p.downloadState() == DownloadState.ERROR).count();
    }

    public List<PackEntry> getPacks() {
        return packs;
    }

    public PackEntry getPackById(String packId) {
        return packs.stream().filter(p -> p.packId().equals(packId)).findFirst().orElse(null);
    }

    public String getOverallStatusText() {
        int total = packs.size();
        int installed = (int) packs.stream().filter(PackEntry::installed).count();
        int downloading = getDownloadingCount();
        int errors = getErrorCount();

        if (errors > 0) {
            return "§c错误:" + errors;
        }
        if (downloading > 0) {
            return "§6下载中:" + downloading;
        }
        if (installed >= total && total > 0) {
            return "§a已完成:" + installed + "/" + total;
        }
        return "§7CRL:" + total;
    }

    public List<Text> getTooltipText() {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal("§l§a加密资源包信息"));
        lines.add(Text.literal(""));
        lines.add(Text.literal("§e必选: " + getRequiredCount() + " | 选装: " + getOptionalCount()));

        int downloading = getDownloadingCount();
        int errors = getErrorCount();
        if (downloading > 0) {
            lines.add(Text.literal("§6下载中: " + downloading + " 个"));
        }
        if (errors > 0) {
            lines.add(Text.literal("§c错误: " + errors + " 个"));
        }

        lines.add(Text.literal(""));
        lines.add(Text.literal("§7资源包列表:"));

        for (PackEntry pack : packs) {
            String status;
            String type = pack.required() ? "§c[必选]" : "§7[选装]";
            String encryption = pack.encrypted() ? " §8(加密)" : "";

            switch (pack.downloadState()) {
                case DOWNLOADING -> {
                    int percent = pack.downloadPercent();
                    status = "§6下载中 " + percent + "%";
                }
                case ERROR -> status = "§c失败";
                case NOT_DOWNLOADING -> {
                    if (pack.installed()) {
                        status = "§a已安装";
                    } else {
                        status = "§7未安装";
                    }
                }
                default -> status = "§7未知";
            }

            String name = pack.displayName() != null && !pack.displayName().isEmpty()
                    ? pack.displayName() : pack.packId();
            lines.add(Text.literal(type + " §f" + name + encryption + " §8- " + status));

            if (pack.downloadState() == DownloadState.ERROR && pack.errorMessage() != null) {
                lines.add(Text.literal("  §c" + pack.errorMessage()));
            }
        }

        lines.add(Text.literal(""));
        lines.add(Text.literal("§7点击服务器后可查看详细进度"));

        return lines;
    }

    public enum DownloadState {
        NOT_DOWNLOADING,
        DOWNLOADING,
        ERROR
    }

    public record PackEntry(
            String packId,
            String displayName,
            String fileName,
            String sha256,
            long fileSize,
            boolean encrypted,
            boolean required,
            boolean installed,
            DownloadState downloadState,
            int downloadPercent,
            String errorMessage
    ) {
        public PackEntry(String packId, String displayName, String fileName, String sha256, long fileSize,
                         boolean encrypted, boolean required, boolean installed) {
            this(packId, displayName, fileName, sha256, fileSize, encrypted, required, installed,
                    DownloadState.NOT_DOWNLOADING, 0, null);
        }
    }
}
