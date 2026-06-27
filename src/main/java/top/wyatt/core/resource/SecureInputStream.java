package top.wyatt.core.resource;

import top.wyatt.core.memory.MemoryWipeUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * 安全输入流
 * 关闭时自动覆写底层明文数据，防止内存残留
 * 支持从字节数组、堆外缓冲区构建
 */
public class SecureInputStream extends InputStream {
    private final InputStream innerStream;
    private byte[] dataBuffer;
    private ByteBuffer directBuffer;
    private boolean closed = false;

    /**
     * 基于堆内字节数组构建
     */
    public SecureInputStream(byte[] data) {
        this.dataBuffer = data;
        this.innerStream = new ByteArrayInputStream(data);
    }

    /**
     * 基于堆外缓冲区构建
     */
    public SecureInputStream(ByteBuffer buffer) {
        this.directBuffer = buffer;
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        this.dataBuffer = data;
        this.innerStream = new ByteArrayInputStream(data);
    }

    @Override
    public int read() throws IOException {
        ensureOpen();
        return innerStream.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        ensureOpen();
        return innerStream.read(b, off, len);
    }

    @Override
    public int available() throws IOException {
        ensureOpen();
        return innerStream.available();
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        innerStream.close();

        // 安全覆写内存
        if (dataBuffer != null) {
            MemoryWipeUtil.wipe(dataBuffer);
            dataBuffer = null;
        }
        if (directBuffer != null) {
            MemoryWipeUtil.wipe(directBuffer);
            directBuffer = null;
        }
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("流已关闭");
        }
    }
}