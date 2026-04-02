package com.business.fa.function;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * 第五课：Function Calling（函数调用）
 *
 * 核心概念：
 * 1. @Tool — 标记一个方法可以被大模型调用，description 告诉模型这个方法是干什么的
 * 2. @ToolParam — 描述参数的含义，帮助模型理解该传什么值
 *
 * 工作流程：
 *   用户问"北京天气怎么样"
 *   → 模型分析后决定调用 getWeather(city="北京")
 *   → Spring AI 自动执行这个 Java 方法
 *   → 把返回值交还给模型
 *   → 模型基于返回值生成自然语言回复
 *
 * 注意：模型不是直接执行代码，而是返回"我想调用某个函数"的指令，
 *       Spring AI 在本地执行后把结果喂回给模型。
 */
@Service
public class WeatherService {

    /**
     * 模拟查询天气（实际项目中你会调用真实的天气 API）
     */
    @Tool(description = "根据城市名称查询当前天气信息")
    public String getWeather(@ToolParam(description = "城市名称，如：北京、上海") String city) {
        // 这里用模拟数据，实际项目替换为真实 API 调用
        return switch (city) {
            case "北京" -> city + "：晴，气温 28°C，空气质量良";
            case "上海" -> city + "：多云，气温 25°C，湿度 78%";
            case "广州" -> city + "：雷阵雨，气温 32°C，注意带伞";
            default -> city + "：晴转多云，气温 26°C";
        };
    }

    /**
     * 再来一个工具：模拟查询时间
     * 模型会根据用户问题自动判断该调哪个方法
     */
    @Tool(description = "查询指定城市的当前时间")
    public String getTime(@ToolParam(description = "城市名称") String city) {
        return city + "当前时间：2026-04-02 14:30:00";
    }

    /**
     * 再来一个工具：模拟查询时间
     * 模型会根据用户问题自动判断该调哪个方法
     */
    @Tool(description = "查询指定城市历史上的武将")
    public String getAuthor(@ToolParam(description = "城市名称") String city) {
        // 这里用模拟数据，实际项目替换为真实 API 调用
        return switch (city) {
            case "北京" -> city + "：朱棣";
            case "上海" -> city + "：陈毅";
            case "广州" -> city + "：孙中山";
            default -> city + "：岳飞";
        };
    }
}
