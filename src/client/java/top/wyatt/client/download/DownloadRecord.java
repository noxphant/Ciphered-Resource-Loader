package top.wyatt.client.download;

public class DownloadRecord {
    private String packId;
    private String packName;
    private String serverAddress;
    private long fileSize;
    private long downloadStartTime;
    private long downloadEndTime;
    private long downloadDuration;
    private boolean success;
    private String errorMessage;

    public DownloadRecord() {}

    public DownloadRecord(String packId, String packName, String serverAddress, long fileSize,
                          long downloadStartTime, long downloadEndTime, boolean success, String errorMessage) {
        this.packId = packId;
        this.packName = packName;
        this.serverAddress = serverAddress;
        this.fileSize = fileSize;
        this.downloadStartTime = downloadStartTime;
        this.downloadEndTime = downloadEndTime;
        this.downloadDuration = downloadEndTime - downloadStartTime;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public String getPackId() {
        return packId;
    }

    public void setPackId(String packId) {
        this.packId = packId;
    }

    public String getPackName() {
        return packName;
    }

    public void setPackName(String packName) {
        this.packName = packName;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public long getDownloadStartTime() {
        return downloadStartTime;
    }

    public void setDownloadStartTime(long downloadStartTime) {
        this.downloadStartTime = downloadStartTime;
    }

    public long getDownloadEndTime() {
        return downloadEndTime;
    }

    public void setDownloadEndTime(long downloadEndTime) {
        this.downloadEndTime = downloadEndTime;
    }

    public long getDownloadDuration() {
        return downloadDuration;
    }

    public void setDownloadDuration(long downloadDuration) {
        this.downloadDuration = downloadDuration;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getHumanReadableSize() {
        if (fileSize <= 0) return "0 B";
        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = fileSize;
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        return String.format("%.2f %s", size, units[unitIndex]);
    }

    public String getHumanReadableDuration() {
        long seconds = downloadDuration / 1000;
        if (seconds < 60) {
            return seconds + " 秒";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;
            return minutes + " 分 " + remainingSeconds + " 秒";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + " 小时 " + minutes + " 分";
        }
    }
}
