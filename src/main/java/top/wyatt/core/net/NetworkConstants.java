package top.wyatt.core.net;

/**
 * 网络通信通用常量
 * 仅存字符串通道名，不依赖MC的Identifier类，保证core包零MC依赖
 * 适配层负责将字符串转换为对应平台的通道标识
 */
public class NetworkConstants {
    private NetworkConstants() {}

    public static final String CHANNEL_NAMESPACE = "ciphered-resource-loader";

    /** 服务端 → 客户端：同步资源包列表 */
    public static final String CHANNEL_SYNC_PACKS = "sync_packs";

    /** 客户端 → 服务端：资源包下载加载完成回执 */
    public static final String CHANNEL_PACK_READY = "pack_ready";

    /** 客户端 → 服务端：请求重发资源包列表 */
    public static final String CHANNEL_REQUEST_PACKS = "request_packs";

    /** 客户端 → 服务端：请求文件传输 */
    public static final String CHANNEL_REQUEST_FILE_TRANSFER = "request_file_transfer";

    /** 服务端 → 客户端：回复文件传输端口 */
    public static final String CHANNEL_FILE_TRANSFER_PORT = "file_transfer_port";
}