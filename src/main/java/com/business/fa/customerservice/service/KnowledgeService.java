package com.business.fa.customerservice.service;

import com.business.fa.config.VectorStorePersistence;
import com.business.fa.customerservice.model.KnowledgeDoc;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 知识库管理服务 - MySQL 持久化
 *
 * 文档元数据存 MySQL knowledge_doc 表
 * 文档内容向量化后存 VectorStore（内存）
 */
@Service
public class KnowledgeService {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final VectorStorePersistence persistence;

    public KnowledgeService(VectorStore vectorStore, JdbcTemplate jdbcTemplate,
                            VectorStorePersistence persistence) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.persistence = persistence;
    }

    /**
     * 上传文本内容，分块后存入向量库 + 元数据存 MySQL
     */
    public KnowledgeDoc addDocument(String name, String source, String content) {
        List<String> chunks = Arrays.stream(content.split("\n\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

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

        // 存入向量库
        vectorStore.add(documents);

        // 持久化向量数据
        persistence.save();

        // 元数据存 MySQL
        String chunkIdsJson = String.join(",", chunkIds);
        jdbcTemplate.update(
                "INSERT INTO knowledge_doc (id, name, source, chunk_count, chunk_ids) VALUES (?, ?, ?, ?, ?)",
                docId, name, source, chunks.size(), chunkIdsJson);

        return new KnowledgeDoc(docId, name, source, chunks.size(), chunkIds, LocalDateTime.now());
    }

    /**
     * 上传文件
     */
    public KnowledgeDoc addDocumentFromFile(String fileName, String source, byte[] fileBytes) {
        String content = new String(fileBytes, StandardCharsets.UTF_8);
        return addDocument(fileName, source, content);
    }

    /**
     * 删除文档
     */
    public boolean deleteDocument(String docId) {
        // 查元数据
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT chunk_ids FROM knowledge_doc WHERE id = ?", docId);
        if (rows.isEmpty()) return false;

        // 从向量库删除
        String chunkIdsStr = (String) rows.get(0).get("chunk_ids");
        if (chunkIdsStr != null && !chunkIdsStr.isBlank()) {
            List<String> chunkIds = Arrays.asList(chunkIdsStr.split(","));
            vectorStore.delete(chunkIds);
        }

        // 持久化向量数据
        persistence.save();

        // 从 MySQL 删除
        jdbcTemplate.update("DELETE FROM knowledge_doc WHERE id = ?", docId);
        return true;
    }

    /**
     * 获取所有文档列表
     */
    public List<KnowledgeDoc> listDocuments() {
        return jdbcTemplate.query(
                "SELECT id, name, source, chunk_count, chunk_ids, create_time FROM knowledge_doc ORDER BY create_time DESC",
                (rs, rowNum) -> {
                    String chunkIdsStr = rs.getString("chunk_ids");
                    List<String> chunkIds = chunkIdsStr != null
                            ? Arrays.asList(chunkIdsStr.split(",")) : List.of();
                    return new KnowledgeDoc(
                            rs.getString("id"),
                            rs.getString("name"),
                            rs.getString("source"),
                            rs.getInt("chunk_count"),
                            chunkIds,
                            rs.getTimestamp("create_time").toLocalDateTime()
                    );
                });
    }

    /**
     * 获取单个文档
     */
    public KnowledgeDoc getDocument(String docId) {
        List<KnowledgeDoc> docs = jdbcTemplate.query(
                "SELECT id, name, source, chunk_count, chunk_ids, create_time FROM knowledge_doc WHERE id = ?",
                (rs, rowNum) -> new KnowledgeDoc(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("source"),
                        rs.getInt("chunk_count"),
                        Arrays.asList(rs.getString("chunk_ids").split(",")),
                        rs.getTimestamp("create_time").toLocalDateTime()
                ), docId);
        return docs.isEmpty() ? null : docs.get(0);
    }
}
