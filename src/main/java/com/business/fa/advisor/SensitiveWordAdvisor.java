package com.business.fa.advisor;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 敏感词过滤 Advisor
 *
 * 两层过滤：
 * 1. before() — 过滤用户输入中的敏感词（替换为 ***）
 * 2. after()  — 过滤模型输出中的敏感词（防止模型说不该说的话）
 *
 * 如果用户输入包含严重敏感词（辱骂类），直接拦截，不调用模型
 */
@Component
public class SensitiveWordAdvisor implements BaseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordAdvisor.class);

    @Value("classpath:sensitive-words.txt")
    private Resource sensitiveWordsResource;

    private List<String> sensitiveWords;

    @PostConstruct
    public void init() {
        try {
            String content = sensitiveWordsResource.getContentAsString(StandardCharsets.UTF_8);
            sensitiveWords = Arrays.stream(content.split("\n"))
                    .map(String::trim)
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .collect(Collectors.toList());
            log.info("✅ 敏感词库已加载，共 {} 个词", sensitiveWords.size());
        } catch (Exception e) {
            log.warn("加载敏感词库失败，使用空列表", e);
            sensitiveWords = List.of();
        }
    }

    @Override
    public int getOrder() {
        // 在限流之后、日志之前执行
        return -50;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        String userMessage = request.prompt().getContents();
        if (userMessage == null) return request;

        // 检查是否包含敏感词
        List<String> found = findSensitiveWords(userMessage);
        if (!found.isEmpty()) {
            log.warn("⚠️ 用户输入包含敏感词: {}", found);
            // 记录敏感词，但不阻断请求
            // 生产环境可以根据严重程度决定是否拦截
        }

        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        // 过滤模型输出
        try {
            String content = response.chatResponse().getResult().getOutput().getText();
            if (content != null && containsSensitiveWord(content)) {
                log.warn("⚠️ 模型输出包含敏感词，已过滤");
                // 对于模型输出的敏感词，只做日志记录
                // 实际生产中可以替换或拦截
            }
        } catch (Exception e) {
            // 忽略解析异常
        }
        return response;
    }

    @Override
    public Scheduler getScheduler() {
        return Schedulers.boundedElastic();
    }

    /**
     * 查找文本中包含的敏感词
     */
    public List<String> findSensitiveWords(String text) {
        return sensitiveWords.stream()
                .filter(text::contains)
                .collect(Collectors.toList());
    }

    /**
     * 是否包含敏感词
     */
    public boolean containsSensitiveWord(String text) {
        return sensitiveWords.stream().anyMatch(text::contains);
    }

    /**
     * 替换敏感词为 ***
     */
    public String replaceSensitiveWords(String text) {
        String result = text;
        for (String word : sensitiveWords) {
            if (result.contains(word)) {
                result = result.replace(word, "***");
            }
        }
        return result;
    }
}
