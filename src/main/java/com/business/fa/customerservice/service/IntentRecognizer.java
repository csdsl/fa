package com.business.fa.customerservice.service;

import com.business.fa.customerservice.model.IntentResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

/**
 * 意图识别服务
 *
 * 用轻量模型（qwen-turbo）做意图分类，省钱又快
 * 返回结构化的 IntentResult 对象
 */
@Service
public class IntentRecognizer {

    private final ChatClient intentClient;

    public IntentRecognizer(OpenAiChatModel chatModel) {
        // 用 qwen-turbo 做意图识别（便宜、快、分类任务够用）
        this.intentClient = ChatClient.builder(chatModel)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("qwen-turbo")
                        .temperature(0.0)  // 分类任务用0温度，确保结果稳定
                        .build())
                .defaultSystem("""
                        你是一个意图分类器。根据用户的消息，判断其意图类别。
                        
                        意图类别：
                        - CONSULT：咨询类（询问产品信息、政策、价格、功能、配送等）
                        - COMPLAINT：投诉类（表达不满、情绪激动、要求赔偿、威胁差评）
                        - REFUND：退换货类（要求退货、换货、退款）
                        - ORDER_QUERY：订单查询类（查订单状态、查物流、查快递）
                        - CHAT：闲聊类（打招呼、感谢、告别、无明确业务意图）
                        
                        判断规则：
                        - 如果用户提到"退""换""退款"，优先判为 REFUND
                        - 如果用户提到"订单号""快递""物流""到哪了"，优先判为 ORDER_QUERY
                        - 如果用户语气愤怒、包含"投诉""太差""骗子""差评"，判为 COMPLAINT
                        - 如果用户在问产品/政策/价格相关问题，判为 CONSULT
                        - 其他情况判为 CHAT
                        """)
                .build();
    }

    /**
     * 识别用户意图
     */
    public IntentResult recognize(String message) {
        return intentClient.prompt()
                .user(message)
                .call()
                .entity(IntentResult.class);
    }
}
