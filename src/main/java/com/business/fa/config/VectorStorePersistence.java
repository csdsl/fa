package com.business.fa.config;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import java.io.File;

/**
 * 向量库持久化工具
 *
 * 提供手动保存方法，同时在应用关闭时自动保存
 * 知识库变更（添加/删除）后需要调用 save() 持久化
 */
public class VectorStorePersistence {

    private static final Logger log = LoggerFactory.getLogger(VectorStorePersistence.class);

    private final SimpleVectorStore vectorStore;
    private final String filePath;

    public VectorStorePersistence(SimpleVectorStore vectorStore, String filePath) {
        this.vectorStore = vectorStore;
        this.filePath = filePath;
    }

    /**
     * 手动保存向量数据到文件
     */
    public void save() {
        File file = new File(filePath);
        file.getParentFile().mkdirs();
        vectorStore.save(file);
        log.info("向量库已保存到: {}", filePath);
    }

    /**
     * 应用关闭时自动保存
     */
    @PreDestroy
    public void onShutdown() {
        save();
    }
}
