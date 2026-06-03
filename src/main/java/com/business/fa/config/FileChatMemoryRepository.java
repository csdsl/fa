package com.business.fa.config;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 文件持久化的 ChatMemoryRepository
 *
 * 每个会话存为一个 JSON 文件：data/memory/{conversationId}.json
 * 重启后自动加载已有的对话历史
 *
 * 生产环境建议换成 MySQL 或 Redis 实现
 * 这里用文件方式是为了学习时零依赖、开箱即用
 */
public class FileChatMemoryRepository implements ChatMemoryRepository {

    private static final Logger log = LoggerFactory.getLogger(FileChatMemoryRepository.class);

    private final Path storageDir;
    private final ObjectMapper objectMapper;

    // 内存缓存，避免每次都读文件
    private final Map<String, List<Message>> cache = new ConcurrentHashMap<>();

    public FileChatMemoryRepository(String storagePath) {
        this.storageDir = Path.of(storagePath);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.isEnabled(SerializationFeature.INDENT_OUTPUT);

        // 确保目录存在
        try {
            Files.createDirectories(storageDir);
        } catch (Exception e) {
            throw new RuntimeException("无法创建对话存储目录: " + storagePath, e);
        }

        // 启动时加载已有的对话
        loadAll();
        log.info("✅ 对话持久化已启用，存储目录: {}，已加载 {} 个会话", storageDir, cache.size());
    }

    @Override
    public List<String> findConversationIds() {
        return new ArrayList<>(cache.keySet());
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return cache.getOrDefault(conversationId, new ArrayList<>());
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        cache.put(conversationId, new ArrayList<>(messages));
        persistToFile(conversationId, messages);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        cache.remove(conversationId);
        try {
            Files.deleteIfExists(getFilePath(conversationId));
        } catch (Exception e) {
            log.warn("删除对话文件失败: {}", conversationId, e);
        }
    }

    private Path getFilePath(String conversationId) {
        // 用安全的文件名
        String safeName = conversationId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return storageDir.resolve(safeName + ".json");
    }

    private void persistToFile(String conversationId, List<Message> messages) {
        try {
            List<Map<String, String>> serializable = messages.stream()
                    .map(msg -> Map.of(
                            "type", msg.getMessageType().name(),
                            "text", msg.getText() != null ? msg.getText() : ""
                    ))
                    .toList();
            objectMapper.writeValue(getFilePath(conversationId).toFile(), serializable);
        } catch (Exception e) {
            log.warn("持久化对话失败: {}", conversationId, e);
        }
    }

    private void loadAll() {
        try (Stream<Path> paths = Files.list(storageDir)) {
            paths.filter(p -> p.toString().endsWith(".json"))
                    .forEach(this::loadFile);
        } catch (Exception e) {
            log.warn("加载对话历史失败", e);
        }
    }

    private void loadFile(Path file) {
        try {
            String fileName = file.getFileName().toString();
            String conversationId = fileName.replace(".json", "");

            List<Map<String, String>> raw = objectMapper.readValue(
                    file.toFile(), new TypeReference<>() {});

            List<Message> messages = raw.stream()
                    .map(this::deserializeMessage)
                    .filter(Objects::nonNull)
                    .toList();

            if (!messages.isEmpty()) {
                cache.put(conversationId, new ArrayList<>(messages));
            }
        } catch (Exception e) {
            log.warn("加载对话文件失败: {}", file, e);
        }
    }

    private Message deserializeMessage(Map<String, String> map) {
        String type = map.get("type");
        String text = map.get("text");
        return switch (type) {
            case "USER" -> new UserMessage(text);
            case "ASSISTANT" -> new AssistantMessage(text);
            case "SYSTEM" -> new SystemMessage(text);
            default -> null;
        };
    }
}
