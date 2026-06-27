package top.wyatt.core.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * HKDF-SHA256 密钥派生工具
 * 基于主密钥派生单块独立密钥，避免全局统一密钥风险
 * 实现标准 HKDF 提取+扩展两步流程
 */
public class KeyDerivation {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int HASH_LENGTH = 32; // SHA-256 输出长度

    private KeyDerivation() {}

    /**
     * 从主密钥派生指定信息的子密钥
     * @param masterKey 主密钥
     * @param salt 盐值（可选，可为空）
     * @param info 上下文信息（如块索引、资源ID）
     * @param outputLength 输出密钥长度（字节）
     * @return 派生后的子密钥
     */
    public static byte[] deriveKey(byte[] masterKey, byte[] salt, String info, int outputLength) {
        // 第一步：提取
        byte[] prk = extract(masterKey, salt);
        // 第二步：扩展
        return expand(prk, info.getBytes(StandardCharsets.UTF_8), outputLength);
    }

    /**
     * HKDF-Extract：提取伪随机密钥
     */
    private static byte[] extract(byte[] keyMaterial, byte[] salt) {
        try {
            if (salt == null || salt.length == 0) {
                salt = new byte[HASH_LENGTH];
            }
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(salt, HMAC_ALGORITHM));
            return mac.doFinal(keyMaterial);
        } catch (Exception e) {
            throw new RuntimeException("HKDF 提取阶段失败", e);
        }
    }

    /**
     * HKDF-Expand：扩展输出密钥
     */
    private static byte[] expand(byte[] prk, byte[] info, int outputLength) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(prk, HMAC_ALGORITHM));

            int blocks = (int) Math.ceil((double) outputLength / HASH_LENGTH);
            byte[] result = new byte[outputLength];
            byte[] previous = new byte[0];
            int offset = 0;

            for (int i = 1; i <= blocks; i++) {
                mac.update(previous);
                mac.update(info);
                mac.update((byte) i);
                byte[] block = mac.doFinal();

                int copyLength = Math.min(block.length, outputLength - offset);
                System.arraycopy(block, 0, result, offset, copyLength);
                offset += copyLength;
                previous = block;
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("HKDF 扩展阶段失败", e);
        }
    }
}