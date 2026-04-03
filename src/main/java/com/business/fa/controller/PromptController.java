package com.business.fa.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 第八课：Prompt Engineering（提示词工程）
 *
 * 核心思想：模型的输出质量，80% 取决于你怎么写 prompt。
 * 同样的模型，好的 prompt 和差的 prompt，效果天差地别。
 */
@RestController
@RequestMapping("/prompt")
public class PromptController {

    private final ChatClient chatClient;

    public PromptController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    // ========== 技巧一：Prompt Template（模板化） ==========

    /**
     * 不好的写法：字符串拼接，难维护，容易出错
     *   "翻译成" + language + "：" + text
     *
     * 好的写法：用 PromptTemplate，变量用 {变量名} 占位
     *
     * 访问: http://localhost:8080/prompt/translate?text=今天天气真好&language=英文
     */
    @GetMapping("/translate")
    public String translate(@RequestParam String text,
                            @RequestParam(defaultValue = "英文") String language) {
        PromptTemplate template = new PromptTemplate("""
                请将以下文本翻译成{language}。
                要求：
                - 保持原文的语气和风格
                - 如果有专业术语，保留原文并在括号中给出翻译
                
                原文：{text}
                """);

        String prompt = template.render(Map.of("text", text, "language", language));

        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    // ========== 技巧二：角色设定（Role Prompting） ==========

    /**
     * 给模型一个明确的角色，它的回答风格和专业度会完全不同
     *
     * 访问: http://localhost:8080/prompt/expert?question=什么是微服务&role=架构师
     * 访问: http://localhost:8080/prompt/expert?question=什么是微服务&role=小学老师
     *
     * 对比两个结果，你会发现回答风格完全不同
     */
    @GetMapping("/expert")
    public String expertAnswer(@RequestParam String question,
                               @RequestParam(defaultValue = "架构师") String role) {
        PromptTemplate systemTemplate = new PromptTemplate("""
                你是一个经验丰富的{role}。
                回答问题时请符合你的角色身份，使用该角色常用的表达方式和专业程度。
                """);

        String systemPrompt = systemTemplate.render(Map.of("role", role));

        return chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .call()
                .content();
    }

    // ========== 技巧三：Few-shot Learning（少样本学习） ==========

    /**
     * 在 prompt 里给模型几个示例，它就能学会你要的输出格式
     * 不需要训练，不需要微调，只靠 prompt 里的例子
     *
     * 访问: http://localhost:8080/prompt/sentiment?text=这个产品太垃圾了，退货都不给退
     * 访问: http://localhost:8080/prompt/sentiment?text=物流很快，包装也很好，下次还买
     */
    @GetMapping("/sentiment")
    public String sentimentAnalysis(@RequestParam String text) {
        return chatClient.prompt()
                .user("""
                        请对用户评论进行情感分析，按以下格式输出：
                        
                        示例1：
                        评论：手机很好用，拍照清晰，电池耐用
                        情感：正面
                        关键词：好用、清晰、耐用
                        置信度：95%%
                        
                        示例2：
                        评论：等了一周才到，包装还是破的
                        情感：负面
                        关键词：慢、破损
                        置信度：90%%
                        
                        示例3：
                        评论：还行吧，一般般
                        情感：中性
                        关键词：一般
                        置信度：75%%
                        
                        现在请分析：
                        评论：%s
                        """.formatted(text))
                .call()
                .content();
    }

    // ========== 技巧四：Chain of Thought（思维链） ==========

    /**
     * 让模型"一步步思考"，而不是直接给答案
     * 对复杂推理问题效果显著
     *
     * 访问: http://localhost:8080/prompt/cot?question=一个水池有两个水管，A管单独注满需要6小时，B管单独注满需要4小时，同时开两个管需要多久注满？
     */
    @GetMapping("/cot")
    public String chainOfThought(@RequestParam String question) {
        return chatClient.prompt()
                .system("""
                        你是一个善于逻辑推理的助手。
                        回答问题时，请按以下步骤：
                        1. 先理解问题，列出已知条件
                        2. 一步一步推理，展示思考过程
                        3. 最后给出明确的答案
                        
                        不要跳步，每一步都要解释清楚。
                        """)
                .user(question)
                .call()
                .content();
    }

    // ========== 技巧五：Guardrails（防护栏） ==========

    /**
     * 限制模型的输出范围，防止它说不该说的话
     * 这在生产环境中非常重要
     *
     * 访问: http://localhost:8080/prompt/safe?message=帮我写一段Java代码
     * 访问: http://localhost:8080/prompt/safe?message=你觉得哪个政党好
     */
    @GetMapping("/safe")
    public String safeChat(@RequestParam String message) {
        return chatClient.prompt()
                .system("""
                        你是一个专业的技术助手，只回答与编程和技术相关的问题。
                        
                        严格遵守以下规则：
                        1. 只回答编程、软件开发、技术架构相关的问题
                        2. 如果用户问政治、宗教、投资建议等非技术话题，礼貌地拒绝并引导回技术话题
                        3. 不要编造不存在的 API 或库
                        4. 如果不确定，明确说"我不确定"
                        5. 回答中不要包含任何个人观点
                        
                        拒绝模板："这个问题超出了我的专业范围，我专注于技术领域。有什么技术问题我可以帮你吗？"
                        """)
                .user(message)
                .call()
                .content();
    }
}
