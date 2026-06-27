package top.wyatt.core.crypto;

import top.wyatt.core.exception.DecryptException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

/**
 * AES-256-GCM 对称加密封装
 * 自带数据完整性校验，防篡改；IV 随机生成，附在密文头部
 * 密文结构：[12字节IV][密文+16字节认证标签]
 */
public class AesGcmCipher {
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128; // 位，对应16字节
    private static final int KEY_LENGTH = 32; // 256位

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AesGcmCipher() {}

    /**
     * 生成 256 位安全随机密钥
     */
    public static byte[] generateKey() {
        byte[] key = new byte[KEY_LENGTH];
        SECURE_RANDOM.nextBytes(key);
        return key;
    }

    /**
     * 加密字节数组，返回带 IV 的完整密文
     * @param plaintext 明文数据
     * @param key 256位密钥
     * @return [IV + 密文 + 认证标签]
     */
    public static byte[] encrypt(byte[] plaintext, byte[] key) {
        validateKey(key);
        try {
            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            SecretKeySpec keySpec = new SecretKeySpec(key, ALGORITHM);
            GCMParameterSpec paramSpec = new GCMParameterSpec(TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, paramSpec);

            byte[] ciphertext = cipher.doFinal(plaintext);
            // 拼接 IV + 密文
            byte[] result = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, result, IV_LENGTH, ciphertext.length);
            return result;
        } catch (Exception e) {
            throw new DecryptException("AES-GCM 加密失败", e);
        }
    }

    /**
     * 解密带 IV 的密文，返回明文
     * @param ciphertextWithIv [IV + 密文 + 认证标签]
     * @param key 256位密钥
     * @return 明文数据
     */
    public static byte[] decrypt(byte[] ciphertextWithIv, byte[] key) {
        validateKey(key);
        if (ciphertextWithIv.length <= IV_LENGTH) {
            throw new DecryptException("密文长度过短，缺少IV");
        }

        try {
            // 分离 IV 和密文
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(ciphertextWithIv, 0, iv, 0, IV_LENGTH);
            byte[] ciphertext = new byte[ciphertextWithIv.length - IV_LENGTH];
            System.arraycopy(ciphertextWithIv, IV_LENGTH, ciphertext, 0, ciphertext.length);

            SecretKeySpec keySpec = new SecretKeySpec(key, ALGORITHM);
            GCMParameterSpec paramSpec = new GCMParameterSpec(TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, paramSpec);

            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new DecryptException("AES-GCM 解密失败，密钥错误或数据已篡改", e);
        }
    }

    /**
     * 校验密钥长度是否合法
     */
    private static void validateKey(byte[] key) {
        if (key == null || key.length != KEY_LENGTH) {
            throw new IllegalArgumentException("AES-256 密钥必须为 32 字节");
        }
    }
}