package top.wyatt.core.pack.index;

import top.wyatt.core.crypto.HashUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 加密包资源索引表
 * 内存中维护路径哈希到索引条目的映射，支持快速查找
 */
public class ResourceIndex {
    private final Map<String, IndexEntry> entryMap = new HashMap<>();

    /**
     * 添加索引条目
     */
    public void addEntry(IndexEntry entry) {
        entryMap.put(entry.getPathHash(), entry);
    }

    /**
     * 根据资源路径查找索引条目
     * @param resourcePath 完整资源路径（如 assets/minecraft/textures/block/stone.png）
     * @return 索引条目，不存在返回null
     */
    public IndexEntry findEntry(String resourcePath) {
        String hash = HashUtil.sha256Hex(resourcePath);
        return entryMap.get(hash);
    }

    /**
     * 判断资源是否存在
     */
    public boolean contains(String resourcePath) {
        return entryMap.containsKey(HashUtil.sha256Hex(resourcePath));
    }

    /**
     * 获取所有索引条目
     */
    public Collection<IndexEntry> getAllEntries() {
        return entryMap.values();
    }

    /**
     * 获取所有路径哈希集合
     */
    public Set<String> getAllPathHashes() {
        return entryMap.keySet();
    }

    /**
     * 清空索引
     */
    public void clear() {
        entryMap.clear();
    }

    /**
     * 资源数量
     */
    public int size() {
        return entryMap.size();
    }
}