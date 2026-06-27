package top.wyatt.core.resource.decoder;

import top.wyatt.core.crypto.AesGcmCipher;
import top.wyatt.core.crypto.KeyDerivation;
import top.wyatt.core.pack.CipheredPackReader;
import top.wyatt.core.pack.index.IndexEntry;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * 模型中间格式解码器
 * 解密顶点、面索引数据，按映射表重排，输出结构化渲染数据
 * 全程无OBJ/MTL原始文件形态
 */
public class ModelDecoder {
    private final CipheredPackReader reader;
    private final byte[] masterKey;

    public ModelDecoder(CipheredPackReader reader, byte[] masterKey) {
        this.reader = reader;
        this.masterKey = masterKey;
    }

    /**
     * 解密并还原模型顶点数据
     * @return 顶点坐标浮点数缓冲（x,y,z 交错）
     */
    public FloatBuffer decodeVertices(IndexEntry entry) {
        byte[] encrypted = reader.readBlock(entry.getBlockOffsets()[0], entry.getBlockLengths()[0]);
        byte[] blockKey = KeyDerivation.deriveKey(
                masterKey, reader.getGlobalSalt(), "model_vertices", 32
        );
        byte[] plain = AesGcmCipher.decrypt(encrypted, blockKey);

        // 按映射表重排顶点
        int[] mapping = entry.getMappingTable();
        int vertexCount = mapping.length;
        FloatBuffer result = FloatBuffer.allocate(vertexCount * 3);
        FloatBuffer rawBuffer = ByteBuffer.wrap(plain).asFloatBuffer();

        for (int i = 0; i < vertexCount; i++) {
            int srcIndex = mapping[i] * 3;
            result.put(rawBuffer.get(srcIndex));
            result.put(rawBuffer.get(srcIndex + 1));
            result.put(rawBuffer.get(srcIndex + 2));
        }
        result.flip();
        return result;
    }

    /**
     * 解密面索引数据
     */
    public IntBuffer decodeFaces(IndexEntry entry) {
        byte[] encrypted = reader.readBlock(entry.getBlockOffsets()[1], entry.getBlockLengths()[1]);
        byte[] blockKey = KeyDerivation.deriveKey(
                masterKey, reader.getGlobalSalt(), "model_faces", 32
        );
        byte[] plain = AesGcmCipher.decrypt(encrypted, blockKey);
        return ByteBuffer.wrap(plain).asIntBuffer();
    }
}