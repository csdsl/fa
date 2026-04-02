package com.business.fa.controller;

import com.business.fa.function.WeatherService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第五课：Function Calling 控制器
 *
 * 关键点：通过 .tools() 把你的 Service 注册给模型
 * 模型会自动判断是否需要调用、调用哪个方法、传什么参数
 */
@RestController
@RequestMapping("/function")
public class FunctionCallController {

    private final ChatClient chatClient;
    private final WeatherService weatherService;

    public FunctionCallController(ChatClient.Builder builder, WeatherService weatherService) {
        this.chatClient = builder.build();
        this.weatherService = weatherService;
    }

    /**
     * 带工具调用的对话
     *
     * 测试：
     * 1. http://localhost:8080/function/chat?message=北京天气怎么样
     *    → 模型会调用 getWeather("北京")，然后基于结果回答
     *
     * 2. http://localhost:8080/function/chat?message=上海现在几点了
     *    → 模型会调用 getTime("上海")
     *
     * 3. http://localhost:8080/function/chat?message=你好
     *    → 模型判断不需要调用任何工具，直接回答
     *
     * 4. http://localhost:8080/function/chat?message=北京天气怎么样，现在几点
     *    → 模型可能同时调用两个方法
     */
    @GetMapping("/chat")
    public String chat(@RequestParam String message) {
        return chatClient.prompt()
                .user(message)
                .tools(weatherService)   // 把整个 Service 注册为工具集
                .call()
                .content();
    }
}
