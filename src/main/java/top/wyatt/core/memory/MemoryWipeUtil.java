package top.wyatt.core.memory;

import java.nio.ByteBuffer;
import java.security.SecureRandom;

/**
 * 安全内存覆写工具
 * 用随机数据覆盖敏感内存，防止明文残留、被内存Dump提取
 */
public class MemoryWipeUtil {
    private static final SecureRandom RANDOM = new SecureRandom();

    private MemoryWipeUtil() {}

    /**
     * 安全覆写字节数组
     * 用随机字节填充后再零填充，双重覆盖防止残留
     */
    public static void wipe(byte[] data) {
        if (data == null) return;
        RANDOM.nextBytes(data);
        for (int i = 0; i < data.length; i++) {
            data[i] = 0;
        }
    }

    /**
     * 安全覆写 ByteBuffer
     * 支持堆内与堆外缓冲区，覆写后重置位置
     */
    public static void wipe(ByteBuffer buffer) {
        if (buffer == null) return;
        buffer.clear();
        byte[] randomBytes = new byte[buffer.remaining()];
        RANDOM.nextBytes(randomBytes);
        buffer.put(randomBytes);
        buffer.clear();
        while (buffer.hasRemaining()) {
            buffer.put((byte) 0);
        }
        buffer.clear();
    }
}