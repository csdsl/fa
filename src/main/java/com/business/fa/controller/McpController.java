package com.business.fa.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 第九课：MCP 风格的工具调用
 *
 * 与第五课 FunctionCallController 的区别：
 * - 第五课：手动指定 .tools(weatherService)，硬编码绑定
 * - 这一课：通过 ToolCallbackProvider 动态发现工具，松耦合
 *
 * 这就是 MCP 的核心思想：工具的注册和发现是分离的。
 * 添加新工具只需要注册一个新的 ToolCallbackProvider bean，
 * 不需要修改 Controller 代码。
 */
@RestController
@RequestMapping("/mcp")
public class McpController {

    private final ChatClient chatClient;
    private final ToolCallbackProvider[] toolProviders;

    public McpController(ChatClient.Builder builder, ToolCallbackProvider[] toolProviders) {
        this.chatClient = builder.build();
        this.toolProviders = toolProviders;
    }

    /**
     * 列出所有已注册的工具
     * 访问: http://localhost:8080/mcp/tools
     */
    @GetMapping("/tools")
    public List<String> listTools() {
        return Arrays.stream(toolProviders)
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .map(callback -> callback.getToolDefinition().name()
                        + " — " + callback.getToolDefinition().description())
                .toList();
    }

    /**
     * 使用动态发现的工具进行对话
     * 工具是自动注入的，不需要手动指定
     *
     * 访问: http://localhost:8080/mcp/chat?message=北京天气怎么样
     */
    @GetMapping("/chat")
    public String chat(@RequestParam String message) {
        return chatClient.prompt()
                .user(message)
                .toolCallbacks(Arrays.stream(toolProviders)
                        .flatMap(p -> Arrays.stream(p.getToolCallbacks()))
                        .toArray(org.springframework.ai.tool.ToolCallback[]::new))
                .call()
                .content();
    }
}
