package com.business.fa.mcp;

import com.business.fa.function.OrderService;
import com.business.fa.function.WeatherService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 第九课：MCP 协议
 *
 * 核心概念：
 * 1. MCP（Model Context Protocol）— 模型上下文协议，标准化 AI 工具的接入方式
 * 2. MCP Server — 提供工具的一方（类似 REST API 的服务端）
 * 3. MCP Client — 消费工具的一方（类似 REST API 的客户端）
 *
 * 为什么需要 MCP？
 * - Function Calling：工具和模型绑定在同一个应用里
 * - MCP：工具可以独立部署，任何支持 MCP 的 AI 应用都能接入
 *
 * 类比：
 * - Function Calling = USB 直连设备
 * - MCP = 蓝牙协议，任何支持蓝牙的设备都能连
 *
 * 这里我们先把现有的工具通过 ToolCallbackProvider 暴露出去，
 * 这是 Spring AI 中最简单的工具注册方式。
 */
@Configuration
public class McpServerConfig {

    /**
     * 把 WeatherService 的 @Tool 方法注册为可被发现的工具
     * 这样 MCP Client 或 ChatClient 都能自动发现并使用这些工具
     */
    @Bean
    public ToolCallbackProvider weatherTools(WeatherService weatherService, OrderService orderService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(weatherService,orderService)
                .build();
    }
}
