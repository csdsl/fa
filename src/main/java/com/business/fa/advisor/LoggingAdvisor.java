package com.business.fa.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * 第十一课：自定义 Advisor — 日志与监控
 *
 * Advisor 是 Spring AI 的拦截器机制（类似 Spring MVC 的 HandlerInterceptor）
 * 你可以在调用模型前后插入自定义逻辑：
 * - before(): 模型调用前 — 记录请求信息
 * - after():  模型调用后 — 记录响应、token 用量、耗时
 *
 * 之前用过的 MessageChatMemoryAdvisor、QuestionAnswerAdvisor 都是 Advisor，
 * 现在你自己写一个。
 */
@Component
public class LoggingAdvisor implements BaseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(LoggingAdvisor.class);

    // 用 ThreadLocal 记录开始时间（线程安全）
    private final ThreadLocal<Long> startTime = new ThreadLocal<>();

    /**
     * 数字越小，before() 越先执行
     * 数字越大，after() 越先执行（反过来的）
     * Advisor     	   order	职责
     * LoggingAdvisor	0	    记录日志和耗时
     * MemoryAdvisor	50	    注入对话历史
     * RAGAdvisor	    100	    检索知识库
     * 执行顺序：
     *
     * 1、Logging.before() — 记录开始时间
     * 2、Memory.before() — 把历史消息塞进 prompt
     * 3、RAG.before() — 检索相关文档塞进 prompt
     * 4、调用模型
     * 5、RAG.after() — 处理检索结果
     * 6、Memory.after() — 保存本轮对话到记忆
     * 7、Logging.after() — 计算耗时、记录 token 用量
     * @return
     */
    @Override
    public int getOrder() {
        // 数字越小越先执行，日志 Advisor 应该最先执行（最外层）
        return 0;
    }

    /**
     * 模型调用前：记录用户的问题
     */
    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        startTime.set(System.currentTimeMillis());

        String userMessage = request.prompt().getContents();
        log.info("📤 用户提问: {}", userMessage);

        return request;
    }

    /**
     * 模型调用后：记录回答、token 用量、耗时
     */
    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        long elapsed = System.currentTimeMillis() - startTime.get();
        startTime.remove();

        // 获取回答内容
        String content = response.chatResponse().getResult().getOutput().getText();
        log.info("📥 模型回答: {}...", content != null && content.length() > 100
                ? content.substring(0, 100) : content);

        // 获取 token 用量
        Usage usage = response.chatResponse().getMetadata().getUsage();
        if (usage != null) {
            log.info("📊 Token 用量 — 输入: {}, 输出: {}, 总计: {}",
                    usage.getPromptTokens(),
                    usage.getCompletionTokens(),
                    usage.getTotalTokens());
        }

        log.info("⏱️ 耗时: {}ms", elapsed);
        log.info("---");

        return response;
    }

    @Override
    public Scheduler getScheduler() {
        return Schedulers.boundedElastic();
    }
}
