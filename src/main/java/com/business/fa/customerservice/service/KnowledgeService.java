package com.business.fa.customerservice.service;

import com.business.fa.customerservice.model.KnowledgeDoc;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识库管理服务
 *
 * 职责：
 * - 文档上传 → 分块 → 向量化存储
 * - 文档删除（从向量库移除）
 * - 文档列表查询
 */
@Service
public class KnowledgeService {

    private final VectorStore vectorStore;

    // 用内存记录文档元数据（生产环境用数据库）
    private final Map<String, KnowledgeDoc> docRegistry = new ConcurrentHashMap<>();

    public KnowledgeService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 上传文本内容，分块后存入向量库
     *
     * @param name   文档名称
     * @param source 来源分类
     * @param content 文本内容
     * @return 文档记录
     */
    public KnowledgeDoc addDocument(String name, String source, String content) {
        // 1. 按空行分段
        List<String> chunks = Arrays.stream(content.split("\n\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        // 2. 创建 Document 对象，带上元数据
        String docId = UUID.randomUUID().toString().substring(0, 8);
        List<String> chunkIds = new ArrayList<>();

        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkId = docId + "_" + i;
            chunkIds.add(chunkId);
            Document doc = new Document(chunkId, chunks.get(i),
                    Map.of("source", source, "docId", docId, "docName", name));
            documents.add(doc);
        }

        // 3. 存入向量库（自动做 Embedding）
        vectorStore.add(documents);

        // 4. 记录元数据
        KnowledgeDoc knowledgeDoc = new KnowledgeDoc(
                docId, name, source, chunks.size(), chunkIds, LocalDateTime.now());
        docRegistry.put(docId, knowledgeDoc);

        return knowledgeDoc;
    }

    /**
     * 上传文件内容
     */
    public KnowledgeDoc addDocumentFromFile(String fileName, String source, byte[] fileBytes) {
        String content = new String(fileBytes, StandardCharsets.UTF_8);
        return addDocument(fileName, source, content);
    }

    /**
     * 删除文档（从向量库和注册表中移除）
     */
    public boolean deleteDocument(String docId) {
        KnowledgeDoc doc = docRegistry.get(docId);
        if (doc == null) return false;

        // 从向量库删除所有关联的 chunk
        vectorStore.delete(doc.getChunkIds());

        // 从注册表移除
        docRegistry.remove(docId);
        return true;
    }

    /**
     * 获取所有文档列表
     */
    public List<KnowledgeDoc> listDocuments() {
        return new ArrayList<>(docRegistry.values());
    }

    /**
     * 获取单个文档详情
     */
    public KnowledgeDoc getDocument(String docId) {
        return docRegistry.get(docId);
    }
}
