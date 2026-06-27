package top.wyatt.core.memory;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 堆外内存池
 * 复用直接内存缓冲区，减少频繁分配开销，统一管理内存释放
 * 解密数据优先使用堆外内存，降低堆Dump提取风险
 */
public class DirectBufferPool {
    private static final int DEFAULT_BUFFER_SIZE = 16 * 1024; // 16KB，与数据块大小对齐
    private static final int MAX_POOL_SIZE = 64; // 最大缓存数量

    private final ConcurrentLinkedQueue<ByteBuffer> pool = new ConcurrentLinkedQueue<>();
    private final AtomicInteger activeCount = new AtomicInteger(0);

    /**
     * 申请一个缓冲区
     * 池中有空闲则复用，无则新建
     */
    public ByteBuffer acquire() {
        ByteBuffer buffer = pool.poll();
        if (buffer == null) {
            buffer = ByteBuffer.allocateDirect(DEFAULT_BUFFER_SIZE);
        }
        buffer.clear();
        activeCount.incrementAndGet();
        return buffer;
    }

    /**
     * 释放缓冲区，归还到池中
     * 归还前自动安全覆写内存
     */
    public void release(ByteBuffer buffer) {
        if (buffer == null || !buffer.isDirect()) return;
        MemoryWipeUtil.wipe(buffer);
        activeCount.decrementAndGet();

        if (pool.size() < MAX_POOL_SIZE) {
            buffer.clear();
            pool.offer(buffer);
        }
    }

    /**
     * 清空池中所有缓冲区，彻底释放内存
     * 游戏退出、断开服务器时调用
     */
    public void clear() {
        while (!pool.isEmpty()) {
            ByteBuffer buffer = pool.poll();
            if (buffer != null) {
                MemoryWipeUtil.wipe(buffer);
                // 直接内存由GC回收，此处仅解除引用
            }
        }
        activeCount.set(0);
    }

    /**
     * 获取当前活跃缓冲区数量
     */
    public int getActiveCount() {
        return activeCount.get();
    }
}