package com.business.fa.customerservice.controller;

import com.business.fa.customerservice.model.KnowledgeDoc;
import com.business.fa.customerservice.service.KnowledgeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 知识库管理接口
 *
 * 支持动态增删知识文档，不需要重启服务
 * 上传的文档会自动分块、向量化、存入向量库
 */
@RestController
@RequestMapping("/cs/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    /**
     * 上传文件到知识库
     *
     * 测试（用 curl 或 Postman）：
     * curl -X POST http://localhost:8080/cs/knowledge/upload \
     *   -F "file=@product-faq.txt" \
     *   -F "source=产品FAQ"
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "source", defaultValue = "通用") String source) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false, "error", "文件不能为空"));
            }

            KnowledgeDoc doc = knowledgeService.addDocumentFromFile(
                    file.getOriginalFilename(), source, file.getBytes());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "上传成功，已分割为 " + doc.getChunkCount() + " 个文档块",
                    "doc", Map.of(
                            "id", doc.getId(),
                            "name", doc.getName(),
                            "source", doc.getSource(),
                            "chunkCount", doc.getChunkCount(),
                            "createTime", doc.getCreateTime().toString()
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false, "error", "上传失败：" + e.getMessage()));
        }
    }

    /**
     * 通过文本内容添加知识
     *
     * 测试：
     * curl -X POST http://localhost:8080/cs/knowledge/add \
     *   -H "Content-Type: application/json" \
     *   -d '{"name":"促销活动","source":"活动","content":"双11活动...\n\n满300减50..."}'
     */
    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> addText(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "未命名文档");
        String source = body.getOrDefault("source", "通用");
        String content = body.get("content");

        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "error", "content 不能为空"));
        }

        KnowledgeDoc doc = knowledgeService.addDocument(name, source, content);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "添加成功，已分割为 " + doc.getChunkCount() + " 个文档块",
                "doc", Map.of(
                        "id", doc.getId(),
                        "name", doc.getName(),
                        "source", doc.getSource(),
                        "chunkCount", doc.getChunkCount(),
                        "createTime", doc.getCreateTime().toString()
                )
        ));
    }

    /**
     * 查看知识库文档列表
     *
     * 访问: http://localhost:8080/cs/knowledge/list
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> list() {
        List<KnowledgeDoc> docs = knowledgeService.listDocuments();
        List<Map<String, Object>> docList = docs.stream().map(doc -> Map.<String, Object>of(
                "id", doc.getId(),
                "name", doc.getName(),
                "source", doc.getSource(),
                "chunkCount", doc.getChunkCount(),
                "createTime", doc.getCreateTime().toString()
        )).toList();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "total", docs.size(),
                "docs", docList
        ));
    }

    /**
     * 删除知识文档
     *
     * 测试：
     * curl -X DELETE http://localhost:8080/cs/knowledge/abc123
     */
    @DeleteMapping("/{docId}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String docId) {
        boolean deleted = knowledgeService.deleteDocument(docId);
        if (deleted) {
            return ResponseEntity.ok(Map.of(
                    "success", true, "message", "文档已删除"));
        } else {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false, "error", "文档不存在：" + docId));
        }
    }
}
