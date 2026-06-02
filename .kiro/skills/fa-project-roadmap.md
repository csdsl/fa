---
inclusion: manual
---

# FA 项目功能规划 & 开发进度

## 项目定位

Spring AI 学习项目 + 智能客服实践，目标是从学习 demo 演进为可展示的完整 AI 应用。

---

## 已完成功能

### 基础学习模块
- [x] ChatClient 基础调用（/chat）
- [x] 流式输出 SSE（/chat/stream）
- [x] 对话记忆 ChatMemory（/memory）
- [x] 结构化输出 entity()（/structured）
- [x] Function Calling 工具调用（/function）
- [x] RAG 检索增强生成（/rag）
- [x] Agent 综合实战（/agent）
- [x] Prompt Engineering 五大技巧（/prompt）
- [x] MCP 协议演示（/mcp/demo）
- [x] 多模型切换策略（/multi）
- [x] 可观测性 LoggingAdvisor（/observe）
- [x] 生产化：限流、重试、异步（/prod）

### 智能客服模块（/cs）
- [x] RAG 知识库（产品FAQ、物流政策、退换货政策）
- [x] 对话记忆（多轮对话，滑动窗口10条）
- [x] 工具调用（查订单、退款申请、转人工工单）
- [x] 限流 + 日志监控
- [x] 流式输出

---

## 待开发功能

### P0 — 优先级最高（让项目完整可展示）

- [x] 聊天前端页面
  - 简单 HTML + CSS + JS，接入智能路由接口 /smart/chat
  - 显示意图标签（咨询/投诉/退货/订单/闲聊）
  - 快捷操作按钮（查订单、退换货、物流咨询、开发票）
  - 打字机 typing 动画
  - 访问地址：http://localhost:8080/

- [x] 知识库管理接口
  - POST /cs/knowledge/upload — 上传文件自动分块向量化
  - POST /cs/knowledge/add — 通过 JSON 文本添加知识
  - GET /cs/knowledge/list — 查看已有知识文档列表
  - DELETE /cs/knowledge/{id} — 删除知识文档（同步从向量库移除）
  - 不用重启即可动态更新知识库

- [ ] 对话持久化
  - ChatMemory 从内存换成 MySQL 或 Redis
  - 重启不丢失对话历史
  - 支持查看历史会话列表

- [x] 多轮意图识别
  - 用结构化输出自动判断用户意图（咨询/投诉/退货/订单查询/闲聊）
  - 不同意图路由到不同处理流程（不同 system prompt + 不同工具集）
  - 投诉类自动升级优先级，走安抚话术+转人工
  - 闲聊类用轻量模型省钱
  - 接口：/smart/chat

### P1 — 技术深度（面试加分）

- [ ] 对话摘要生成
  - 对话结束后用模型自动生成一段摘要
  - 存储到数据库，供人工客服回溯
  - 技术：用轻量模型（qwen-turbo）生成摘要节省成本

- [ ] 敏感词过滤 Advisor
  - 自定义 Advisor，在 before/after 中拦截敏感内容
  - 用户输入过滤（辱骂、违规内容）
  - 模型输出过滤（防止模型说不该说的话）

- [ ] Token 用量统计
  - 按用户、按接口、按天统计 token 消耗
  - 提供查询接口 GET /admin/token-usage
  - 月度用量报表、预算告警

- [ ] 语义缓存
  - 相似问题命中缓存直接返回，不调用模型
  - 用向量相似度判断是否命中（阈值 0.95）
  - 大幅节省 API 成本

- [ ] 多租户隔离
  - 不同商家接入同一客服系统
  - 知识库按租户隔离
  - 对话记录按租户隔离
  - 配置按租户独立（system prompt、模型选择等）

### P2 — 体验优化

- [ ] WebSocket 实时对话
  - 替代 HTTP 轮询 + SSE
  - 真正的双向实时通信
  - 支持打字状态提示

- [ ] 满意度评价
  - 对话结束后自动请求评分（1-5星）
  - 关联到 session 记录
  - 低分自动触发人工回访

- [ ] 主动推送通知
  - 物流状态变更时主动通知用户
  - 结合 MQ（RabbitMQ）监听业务事件
  - WebSocket/SSE 推送到前端

### P3 — 高级能力

- [ ] 多模态支持
  - 用户发图片（商品损坏照片）
  - 模型识别后辅助判断是否符合退货条件
  - 千问 VL 模型支持图片理解

- [ ] Agent 工作流编排
  - 复杂退款流程多步自动完成
  - 审核 → 通知 → 退款 → 确认
  - 失败重试 + 人工介入节点

- [ ] 管理后台
  - 对话记录查看和检索
  - 知识库可视化管理
  - Token 用量报表 + Grafana 集成
  - 客服绩效统计

---

## 技术选型备忘

| 功能 | 技术方案 |
|------|----------|
| 对话持久化 | MySQL（spring-ai-memory-repository-jdbc）或 Redis |
| 前端页面 | 简单 HTML + Tailwind CSS + EventSource |
| 文件上传 | Spring MVC MultipartFile + 向量化 |
| WebSocket | spring-boot-starter-websocket |
| 语义缓存 | SimpleVectorStore 做相似度匹配 |
| 多租户 | ThreadLocal + 拦截器 + 数据隔离 |
| 消息队列 | RabbitMQ（已有经验） |

---

## 开发节奏建议

- 每周完成 1-2 个功能点
- P0 全部完成后项目可以写进简历展示
- P1 挑 2-3 个完成即可体现技术深度
- P2/P3 根据时间和兴趣选做
