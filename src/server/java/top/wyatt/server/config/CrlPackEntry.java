package top.wyatt.server.config;

public class CrlPackEntry {
    private String packId;
    private String displayName;
    private String fileName;
    private String sha256;
    private long fileSize;
    private boolean encrypted;
    private boolean required;
    private String decryptKey;

    public CrlPackEntry() {}

    public CrlPackEntry(String packId, String displayName, String fileName, String sha256, long fileSize, boolean encrypted, boolean required, String decryptKey) {
        this.packId = packId;
        this.displayName = displayName;
        this.fileName = fileName;
        this.sha256 = sha256;
        this.fileSize = fileSize;
        this.encrypted = encrypted;
        this.required = required;
        this.decryptKey = decryptKey;
    }

    public String getPackId() { return packId; }
    public void setPackId(String packId) { this.packId = packId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public boolean isEncrypted() { return encrypted; }
    public void setEncrypted(boolean encrypted) { this.encrypted = encrypted; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public String getDecryptKey() { return decryptKey; }
    public void setDecryptKey(String decryptKey) { this.decryptKey = decryptKey; }
}