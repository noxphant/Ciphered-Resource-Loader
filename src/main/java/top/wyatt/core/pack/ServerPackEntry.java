package top.wyatt.core.pack;

/**
 * 服务端资源包条目
 * 描述单个加密资源包的元数据，用于服务端下发
 */
public class ServerPackEntry {
    // 资源包唯一ID
    public String packId;
    // 显示名称
    public String displayName;
    // 版本号
    public String version;
    // 下载直链
    public String downloadUrl;
    // 文件SHA256哈希，用于完整性校验
    public String sha256;
    // 文件大小（字节）
    public long fileSize;
    // 是否为必选资源包
    public boolean required;
    // 解密密钥（会话级，服务端动态下发）
    public String decryptKey;

    public ServerPackEntry() {}
}