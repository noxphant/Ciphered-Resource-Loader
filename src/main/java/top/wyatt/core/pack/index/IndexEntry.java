package top.wyatt.core.pack.index;

import top.wyatt.core.resource.ResourceType;

/**
 * 单个资源的索引条目
 * 描述资源在加密包中的位置、类型、分片信息
 */
public class IndexEntry {
    // 资源路径哈希（SHA-256）
    private String pathHash;
    // 资源类型
    private ResourceType resourceType;
    // 数据块偏移列表（相对于文件起始的字节偏移）
    private long[] blockOffsets;
    // 每个块的有效数据长度
    private int[] blockLengths;
    // 总数据长度（解密后）
    private int totalLength;
    // 瓦片/顶点映射表（中间格式还原用）
    private int[] mappingTable;

    public IndexEntry() {}

    // Getter & Setter
    public String getPathHash() { return pathHash; }
    public void setPathHash(String pathHash) { this.pathHash = pathHash; }

    public ResourceType getResourceType() { return resourceType; }
    public void setResourceType(ResourceType resourceType) { this.resourceType = resourceType; }

    public long[] getBlockOffsets() { return blockOffsets; }
    public void setBlockOffsets(long[] blockOffsets) { this.blockOffsets = blockOffsets; }

    public int[] getBlockLengths() { return blockLengths; }
    public void setBlockLengths(int[] blockLengths) { this.blockLengths = blockLengths; }

    public int getTotalLength() { return totalLength; }
    public void setTotalLength(int totalLength) { this.totalLength = totalLength; }

    public int[] getMappingTable() { return mappingTable; }
    public void setMappingTable(int[] mappingTable) { this.mappingTable = mappingTable; }
}