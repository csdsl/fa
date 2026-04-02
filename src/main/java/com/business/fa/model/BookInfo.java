package com.business.fa.model;

/**
 * 用 record 定义一个简单的数据结构
 * Spring AI 会让模型按这个结构返回 JSON，然后自动反序列化成 Java 对象
 */
public record BookInfo(
        String title,       // 书名
        String author,      // 作者
        int year,           // 出版年份
        String summary      // 简介
) {}
