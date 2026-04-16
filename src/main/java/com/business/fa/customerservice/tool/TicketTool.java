package com.business.fa.customerservice.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 工单工具 — 模型判断需要人工介入时，自动创建工单转人工
 */
@Component
public class TicketTool {

    private final AtomicInteger ticketCounter = new AtomicInteger(5000);

    @Tool(description = "当客服无法解决用户问题时，创建人工客服工单，将对话转交给人工处理")
    public String createHumanTicket(
            @ToolParam(description = "用户问题的简要描述") String summary,
            @ToolParam(description = "紧急程度：低、中、高") String priority) {

        String ticketId = "TK" + ticketCounter.incrementAndGet();
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        return String.format("""
                已为您创建人工客服工单：
                工单号: %s
                问题描述: %s
                紧急程度: %s
                创建时间: %s
                
                人工客服会在30分钟内联系您，请保持手机畅通。
                如有紧急问题，也可拨打客服热线 400-xxx-xxxx。""", ticketId, summary, priority, now);
    }
}
