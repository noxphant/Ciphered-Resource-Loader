package top.wyatt.core.pack;

import top.wyatt.core.crypto.AesGcmCipher;
import top.wyatt.core.exception.InvalidPackException;
import top.wyatt.core.exception.WrongKeyException;
import top.wyatt.core.pack.index.IndexEntry;
import top.wyatt.core.pack.index.ResourceIndex;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * 加密包读取器
 * 基于随机访问文件实现，支持按需读取、无需加载整个文件到内存
 * 负责文件头校验、索引解密、数据块读取
 */
public class CipheredPackReader implements AutoCloseable {
    private final File packFile;
    private RandomAccessFile raf;
    private int formatVersion;
    private byte algorithmType;
    private long indexOffset;
    private int indexLength;
    private byte[] globalSalt;

    private ResourceIndex resourceIndex;
    private boolean indexLoaded = false;

    public CipheredPackReader(File packFile) {
        this.packFile = packFile;
    }

    /**
     * 打开文件并解析文件头
     * 仅读取明文区域，无需密钥
     */
    public void openAndParseHeader() {
        try {
            raf = new RandomAccessFile(packFile, "r");
            parseHeader();
        } catch (IOException e) {
            throw new InvalidPackException("无法读取加密包文件", e);
        }
    }

    /**
     * 解析文件头各字段
     */
    private void parseHeader() throws IOException {
        // 校验魔数
        byte[] magic = new byte[CipheredPackFormat.MAGIC_LENGTH];
        raf.readFully(magic);
        for (int i = 0; i < CipheredPackFormat.MAGIC_LENGTH; i++) {
            if (magic[i] != CipheredPackFormat.MAGIC[i]) {
                throw new InvalidPackException("无效的加密包格式，魔数不匹配");
            }
        }

        // 读取版本
        formatVersion = Short.toUnsignedInt(raf.readShort());
        if (formatVersion > CipheredPackFormat.CURRENT_VERSION) {
            throw new InvalidPackException("加密包版本过高，请升级模组");
        }

        // 读取算法标识
        algorithmType = raf.readByte();

        // 读取索引区偏移与长度
        indexOffset = raf.readLong();
        indexLength = raf.readInt();

        // 读取全局盐值
        long saltOffset = raf.readLong();
        int saltLength = Byte.toUnsignedInt(raf.readByte());
        raf.seek(saltOffset);
        globalSalt = new byte[saltLength];
        raf.readFully(globalSalt);
    }

    /**
     * 使用主密钥加载并解密索引区
     * @param masterKey 主密钥字节数组
     */
    public void loadIndex(byte[] masterKey) {
        if (raf == null) {
            throw new IllegalStateException("请先调用 openAndParseHeader()");
        }
        try {
            // 读取加密的索引数据
            raf.seek(indexOffset);
            byte[] encryptedIndex = new byte[indexLength];
            raf.readFully(encryptedIndex);

            // 解密索引
            byte[] indexBytes;
            try {
                indexBytes = AesGcmCipher.decrypt(encryptedIndex, masterKey);
            } catch (Exception e) {
                throw new WrongKeyException("密钥错误，无法解密索引", e);
            }

            // 反序列化索引表（简化版，实际可使用protobuf/自定义二进制格式）
            resourceIndex = deserializeIndex(indexBytes);
            indexLoaded = true;
        } catch (IOException e) {
            throw new InvalidPackException("读取索引区失败", e);
        }
    }

    /**
     * 读取指定偏移和长度的原始加密数据块
     */
    public byte[] readBlock(long offset, int length) {
        try {
            raf.seek(offset);
            byte[] data = new byte[length];
            raf.readFully(data);
            return data;
        } catch (IOException e) {
            throw new InvalidPackException("读取数据块失败", e);
        }
    }

    /**
     * 根据资源路径获取索引条目
     */
    public IndexEntry getResourceEntry(String resourcePath) {
        if (!indexLoaded) {
            throw new IllegalStateException("索引未加载，请先提供正确密钥");
        }
        return resourceIndex.findEntry(resourcePath);
    }

    /**
     * 检查资源是否存在
     */
    public boolean hasResource(String resourcePath) {
        if (!indexLoaded) return false;
        return resourceIndex.contains(resourcePath);
    }

    /**
     * 反序列化索引表（简化实现，实际项目建议使用紧凑二进制格式）
     */
    private ResourceIndex deserializeIndex(byte[] bytes) {
        // 示例：简单的字符串反序列化，正式实现请用二进制协议
        ResourceIndex index = new ResourceIndex();
        String content = new String(bytes, StandardCharsets.UTF_8);
        // 此处省略具体反序列化逻辑，按制作端约定的格式解析
        return index;
    }

    // Getter
    public int getFormatVersion() { return formatVersion; }
    public byte getAlgorithmType() { return algorithmType; }
    public byte[] getGlobalSalt() { return globalSalt; }
    public boolean isIndexLoaded() { return indexLoaded; }
    public ResourceIndex getResourceIndex() { return resourceIndex; }

    @Override
    public void close() {
        try {
            if (raf != null) {
                raf.close();
            }
            if (resourceIndex != null) {
                resourceIndex.clear();
            }
            indexLoaded = false;
        } catch (IOException ignored) {}
    }
}