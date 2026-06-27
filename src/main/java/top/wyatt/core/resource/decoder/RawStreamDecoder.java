package top.wyatt.core.resource.decoder;

import top.wyatt.core.crypto.AesGcmCipher;
import top.wyatt.core.crypto.KeyDerivation;
import top.wyatt.core.exception.DecryptException;
import top.wyatt.core.pack.CipheredPackReader;
import top.wyatt.core.pack.index.IndexEntry;
import top.wyatt.core.resource.SecureInputStream;

import java.io.InputStream;

/**
 * 兼容模式：原始文件流解码器
 * 解密所有数据块并拼接为完整原始文件流，严格兼容MC原生加载
 * 适用于配置文件、未知类型资源，防护等级中等
 */
public class RawStreamDecoder {
    private final CipheredPackReader reader;
    private final byte[] masterKey;

    public RawStreamDecoder(CipheredPackReader reader, byte[] masterKey) {
        this.reader = reader;
        this.masterKey = masterKey;
    }

    /**
     * 解密指定资源，返回完整原始字节流
     */
    public InputStream decode(IndexEntry entry) {
        long[] offsets = entry.getBlockOffsets();
        int[] lengths = entry.getBlockLengths();
        byte[] result = new byte[entry.getTotalLength()];
        int offset = 0;

        for (int i = 0; i < offsets.length; i++) {
            // 读取加密块
            byte[] encryptedBlock = reader.readBlock(offsets[i], lengths[i]);
            // 派生单块密钥
            byte[] blockKey = KeyDerivation.deriveKey(
                    masterKey, reader.getGlobalSalt(), "block_" + i, 32
            );
            // 解密块
            byte[] plainBlock;
            try {
                plainBlock = AesGcmCipher.decrypt(encryptedBlock, blockKey);
            } catch (Exception e) {
                throw new DecryptException("第 " + i + " 数据块解密失败", e);
            }
            // 拼接到结果
            System.arraycopy(plainBlock, 0, result, offset, plainBlock.length);
            offset += plainBlock.length;
        }

        return new SecureInputStream(result);
    }
}