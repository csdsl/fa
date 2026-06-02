package com.business.fa.customerservice.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识文档记录
 */
public class KnowledgeDoc {
    private String id;
    private String name;          // 文档名称
    private String source;        // 来源分类（产品FAQ/物流政策/退换货等）
    private int chunkCount;       // 切分的块数
    private List<String> chunkIds; // 向量库中的文档ID列表
    private LocalDateTime createTime;

    public KnowledgeDoc() {}

    public KnowledgeDoc(String id, String name, String source, int chunkCount,
                        List<String> chunkIds, LocalDateTime createTime) {
        this.id = id;
        this.name = name;
        this.source = source;
        this.chunkCount = chunkCount;
        this.chunkIds = chunkIds;
        this.createTime = createTime;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public int getChunkCount() { return chunkCount; }
    public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }
    public List<String> getChunkIds() { return chunkIds; }
    public void setChunkIds(List<String> chunkIds) { this.chunkIds = chunkIds; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
