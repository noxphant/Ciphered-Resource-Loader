package top.wyatt.core.resource;

/**
 * 资源类型枚举
 * 用于区分不同的解码策略与中间格式
 */
public enum ResourceType {
    /** 纹理图片 */
    TEXTURE,
    /** 模型文件 */
    MODEL,
    /** 音频文件 */
    AUDIO,
    /** 配置/文本文件 */
    CONFIG,
    /** 未知类型，走原始流兼容模式 */
    UNKNOWN
}