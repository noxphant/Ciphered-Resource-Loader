package top.wyatt.core.resource.decoder;

import top.wyatt.core.crypto.AesGcmCipher;
import top.wyatt.core.crypto.KeyDerivation;
import top.wyatt.core.pack.CipheredPackReader;
import top.wyatt.core.pack.index.IndexEntry;

import java.nio.ByteBuffer;

/**
 * 纹理中间格式解码器
 * 解密瓦片像素数据，按映射表重组为完整RGBA像素缓冲
 * 输出原始像素数据，直接上传GPU，全程无PNG文件形态
 */
public class TextureDecoder {
    private static final int TILE_SIZE = 16; // 瓦片大小：16x16像素
    private static final int BYTES_PER_PIXEL = 4; // RGBA

    private final CipheredPackReader reader;
    private final byte[] masterKey;

    public TextureDecoder(CipheredPackReader reader, byte[] masterKey) {
        this.reader = reader;
        this.masterKey = masterKey;
    }

    /**
     * 解密并重组纹理，返回RGBA像素缓冲区
     * @param entry 资源索引条目
     * @param width 纹理宽度
     * @param height 纹理高度
     * @return RGBA格式像素缓冲（堆外内存）
     */
    public ByteBuffer decodeRgba(IndexEntry entry, int width, int height) {
        // 1. 解密所有瓦片数据
        byte[][] tiles = decryptTiles(entry);
        int[] mapping = entry.getMappingTable();

        // 2. 按映射表重组像素
        int tileCountX = (width + TILE_SIZE - 1) / TILE_SIZE;
        ByteBuffer result = ByteBuffer.allocateDirect(width * height * BYTES_PER_PIXEL);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int tileX = x / TILE_SIZE;
                int tileY = y / TILE_SIZE;
                int tileIndex = tileY * tileCountX + tileX;
                int mappedTileIndex = mapping[tileIndex];

                int innerX = x % TILE_SIZE;
                int innerY = y % TILE_SIZE;
                int pixelOffset = (innerY * TILE_SIZE + innerX) * BYTES_PER_PIXEL;

                byte[] tile = tiles[mappedTileIndex];
                result.put(tile[pixelOffset]);
                result.put(tile[pixelOffset + 1]);
                result.put(tile[pixelOffset + 2]);
                result.put(tile[pixelOffset + 3]);
            }
        }
        result.flip();
        return result;
    }

    /**
     * 解密所有瓦片
     */
    private byte[][] decryptTiles(IndexEntry entry) {
        long[] offsets = entry.getBlockOffsets();
        int[] lengths = entry.getBlockLengths();
        byte[][] tiles = new byte[offsets.length][];

        for (int i = 0; i < offsets.length; i++) {
            byte[] encrypted = reader.readBlock(offsets[i], lengths[i]);
            byte[] blockKey = KeyDerivation.deriveKey(
                    masterKey, reader.getGlobalSalt(), "tile_" + i, 32
            );
            tiles[i] = AesGcmCipher.decrypt(encrypted, blockKey);
        }
        return tiles;
    }
}