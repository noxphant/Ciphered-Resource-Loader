package top.wyatt.core.key;

import top.wyatt.core.crypto.HashUtil;

import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话级密钥管理器
 * 线程安全，仅存储于内存，不持久化到磁盘
 * 支持按资源包ID管理多个密钥，断开服务器自动清理
 */
public class KeyManager {
    // key: 资源包ID，value: 256位原始密钥字节数组
    private final ConcurrentHashMap<String, byte[]> keyStore = new ConcurrentHashMap<>();

    /**
     * 添加密钥（Base64 字符串形式）
     */
    public void putKeyBase64(String packId, String base64Key) {
        byte[] key = Base64.getDecoder().decode(base64Key);
        keyStore.put(packId, key);
    }

    /**
     * 添加密钥（原始字节数组形式）
     */
    public void putKeyBytes(String packId, byte[] key) {
        keyStore.put(packId, key.clone());
    }

    /**
     * 获取指定资源包的密钥
     * @return 密钥字节数组，不存在返回null
     */
    public byte[] getKey(String packId) {
        byte[] key = keyStore.get(packId);
        return key != null ? key.clone() : null;
    }

    /**
     * 检查是否存在指定资源包的密钥
     */
    public boolean hasKey(String packId) {
        return keyStore.containsKey(packId);
    }

    /**
     * 移除指定资源包的密钥
     */
    public void removeKey(String packId) {
        byte[] key = keyStore.remove(packId);
        if (key != null) {
            // 安全覆写后丢弃
            for (int i = 0; i < key.length; i++) {
                key[i] = 0;
            }
        }
    }

    /**
     * 清空所有服务端会话密钥
     * 断开服务器时调用
     */
    public void clearServerSessionKeys() {
        // 覆写所有密钥内存后清空
        for (byte[] key : keyStore.values()) {
            for (int i = 0; i < key.length; i++) {
                key[i] = 0;
            }
        }
        keyStore.clear();
    }

    /**
     * 计算密钥的指纹（用于展示，不暴露完整密钥）
     */
    public String getKeyFingerprint(String packId) {
        byte[] key = keyStore.get(packId);
        if (key == null) return "未设置";
        return HashUtil.sha256Hex(key).substring(0, 8);
    }
}