# Ragent AI 整体架构文档

> **面向后端程序员的 AI 工程化教学 Demo**  
> 项目地址: [github.com/nageoffer/ragent](https://github.com/nageoffer/ragent) | License: Apache 2.0

---

## 1. 项目定位

Ragent AI 是一个**企业级 Agentic RAG（Retrieval-Augmented Generation）平台**，完整覆盖从文档入库、向量检索、意图识别、MCP 工具调用到流式对话的智能体全链路。项目不依赖 Spring AI / LangChain4j 等高层框架，而是**自建抽象层**，用后端工程师熟悉的 Spring Boot 技术栈将 AI 应用工程化落地。

**核心价值：**  
- 让你理解 RAG 系统的底层运作机制，而非只会调 API
- 生产级工程实践（多模型调度、检索优化、流水线编排）
- 面试/简历中可深入聊的 AI 工程化项目

---

## 2. 技术栈一览

| 层次 | 技术 | 说明 |
|------|------|------|
| **语言** | Java 17 | LTS 版本 |
| **框架** | Spring Boot 3.5.7 | 主应用框架 |
| **数据库** | PostgreSQL + pgvector | 关系型存储 + 向量检索 |
| **向量库(可选)** | Milvus 2.6+ | 专用向量数据库 |
| **缓存** | Redis + Redisson 4.0 | 缓存 / 分布式锁 / 信号量 |
| **消息队列** | RocketMQ | 文档分块异步处理（事务消息） |
| **ORM** | MyBatis-Plus 3.5.14 | 数据库访问层 |
| **鉴权** | Sa-Token 1.43 | Session / Redis 双模式 |
| **对象存储** | S3 兼容（MinIO/RustFS） | 文档文件存储 |
| **MCP 协议** | MCP SDK 1.1.2 | Model Context Protocol 工具集成 |
| **文档解析** | Apache Tika 3.2 | PDF / DOC / DOCX / MD 多格式 |
| **前端** | React + TypeScript + Vite + Tailwind CSS | SPA 管理后台 |
| **构建** | Maven 多模块 | 4 个子模块 |

---

## 3. 模块架构

```
ragent/
├── bootstrap/          # [启动 & 业务层] Spring Boot 入口 + 全部业务代码
│   └── src/main/java/com/nageoffer/ai/ragent/
│       ├── rag/        # RAG 对话核心（检索、意图、Prompt、记忆、MCP、流式）
│       ├── knowledge/  # 知识库管理（文档 CRUD、分块入库、定时刷新）
│       ├── ingestion/  # 文档摄入流水线（Fetcher→Parser→Chunker→...→Indexer）
│       ├── user/       # 用户认证（Sa-Token 配置）
│       ├── admin/      # 运营仪表盘
│       └── core/       # 通用：文档解析、分块策略、嵌入服务
│
├── framework/          # [基础设施层] 与 AI 无关的通用能力
│   └── src/main/java/com/nageoffer/ai/ragent/framework/
│       ├── config/     # 数据库/Redis/Web 自动配置
│       ├── cache/      # Redis Key 序列化
│       ├── convention/ # 统一返回体 Result、ChatMessage、ChatRequest
│       ├── context/    # 用户上下文（TTL 线程传递）
│       ├── database/   # MyBatis 自动填充、雪花 ID
│       ├── exception/  # 异常体系（Client/Remote/Service）
│       ├── idempotent/ # 幂等控制（AOP 注解）
│       ├── mq/         # RocketMQ 生产者抽象
│       ├── trace/      # RAG 链路追踪上下文
│       └── web/        # 全局异常处理、SSE 发送器
│
├── infra-ai/           # [AI 基础设施层] LLM/Embedding/Rerank 调用抽象
│   └── src/main/java/com/nageoffer/ai/ragent/infra/
│       ├── chat/       # 对话模型客户端（百炼/硅基/Ollama）+ 流式/路由
│       ├── embedding/  # 嵌入模型客户端（硅基/Ollama）+ 路由
│       ├── rerank/     # 重排序客户端（百炼/空实现）
│       ├── model/      # 模型选择器、健康检查、熔断降级
│       ├── token/      # Token 计数（启发式算法）
│       ├── http/       # HTTP 客户端、异常处理
│       └── enums/      # Provider/Capability 枚举
│
├── mcp-server/         # [独立 MCP Server] 业务工具暴露为 MCP 协议
│   └── src/main/java/.../mcp/
│       ├── executor/   # 天气、工单、销售 示例 Executor
│       └── config/     # MCP Server 配置
│
├── frontend/           # React SPA 前端
│   └── src/
│       ├── pages/      # 页面（ChatPage、LoginPage、Admin）
│       ├── components/ # 组件（chat/、layout/、ui/、session/）
│       ├── stores/     # Zustand 状态管理
│       ├── services/   # API 服务层
│       └── types/      # TypeScript 类型定义
│
├── docs/               # 文档
├── scripts/            # 运维脚本（SSE 测试等）
└── resources/          # 代码模板（版权头等）
```

### 模块依赖关系

```
bootstrap  ──→  framework  (基础设施)
bootstrap  ──→  infra-ai   (AI 调用)
infra-ai   ──→  framework  (基础设施)
mcp-server  ─→  (独立部署，无模块依赖)
```

---

## 4. 核心业务流程

### 4.1 流式对话流水线（6 阶段）

这是系统的**核心链路**，由 `StreamChatPipeline` 编排：

```
┌──────────────────────────────────────────────────────────────────────┐
│                     StreamChatPipeline.execute()                      │
│                                                                      │
│  ① loadMemory ─→ ② rewriteQuery ─→ ③ resolveIntents                │
│       │                │                    │                        │
│   加载历史对话      Query改写+拆分        树形意图分类                  │
│   记忆摘要注入      术语映射替换          置信度评分                    │
│                                                     │                │
│                          ┌──────────────────────────┘                │
│                          ▼                                            │
│              ④ handleGuidance  ←── 置信度不足？引导用户澄清            │
│                     │ (短路)                                          │
│                     ▼ (继续)                                         │
│              ⑤ retrieve  ←──  多路检索 + 去重 + Rerank               │
│                     │                                                 │
│                     ▼                                                 │
│              ⑥ streamRagResponse  ←──  Prompt组装 + SSE流式输出       │
└──────────────────────────────────────────────────────────────────────┘
```

**各阶段详细：**

| 阶段 | 组件 | 说明 |
|------|------|------|
| **记忆加载** | `ConversationMemoryService` | 加载最近 N 轮历史 + 历史摘要压缩注入 |
| **Query改写** | `MultiQuestionRewriteService` | LLM 驱动改写：术语替换 + 复杂问题拆分为子问题 |
| **意图解析** | `IntentResolver` + `DefaultIntentClassifier` | 树形多级分类，每个子问题匹配意图节点 |
| **歧义引导** | `IntentGuidanceService` + `AmbiguityLLMChecker` | 置信度 < 阈值时 LLM 生成引导语，短路返回 |
| **多路检索** | `MultiChannelRetrievalEngine` | VectorGlobal（阈值过滤）+ IntentDirected（分类限制）|
| **Prompt组装** | `RAGPromptService` + `ContextFormatter` | 知识库/MCP 结果融合，构建结构化消息发往 LLM |

### 4.2 文档摄入流水线（链式 DAG）

由 `IngestionEngine` 驱动的**可配置节点链**：

```
Fetcher ──→ Parser ──→ Chunker ──→ Enricher ──→ Enhancer ──→ Indexer
  │           │           │           │             │            │
 下载文件    Tika解析    文档分块   元数据丰富    LLM增强补充   向量写入
(S3/URL)   PDF/DOC/MD  定长/结构化  (关键词等)   (摘要/QA对)  (PG/Milvus)
```

**配置驱动：** 流水线通过 `PipelineDefinition` + `NodeConfig` JSON 定义节点和连线，支持：
- 节点条件跳过 (`condition` 表达式求值)
- 节点间任意连线 (`nextNodeId`)
- 环检测 + 最大执行数保护
- 每节点执行日志追踪

---

## 5. 关键设计深入

### 5.1 意图分类体系

```
                    ┌──────────┐
                    │  ROOT    │
                    └────┬─────┘
              ┌──────────┼──────────┐
              ▼                     ▼
         ┌─────────┐          ┌─────────┐
         │  KB意图  │          │ MCP意图  │
         └────┬────┘          └────┬────┘
      ┌───────┼───────┐      ┌────┼────┐
      ▼       ▼       ▼      ▼    ▼    ▼
  技术文档  运维手册  产品文档  天气  工单  销售
  (二级)   (二级)   (二级)  查询  查询  统计
```

**关键组件：**
- `IntentNode`：树节点，包含分类描述、Prompt 模板、子节点列表
- `IntentTreeFactory`：从 DB 加载意图树并缓存
- `IntentResolver`：调用 LLM 做分类匹配，计算每个节点的置信度得分
- `NodeScoreFilters`：根据阈值过滤低分节点
- **system_only 节点**：配置了 Prompt 模板的节点可走纯对话模式，不走检索

### 5.2 多路检索引擎

```
MultiChannelRetrievalEngine
        │
        ├── VectorGlobalSearchChannel   (全局向量检索)
        │   └── 置信度阈值过滤 (confidence-threshold: 0.6)
        │   └── TopK 倍数控制 (top-k-multiplier: 3)
        │
        ├── IntentDirectedSearchChannel  (意图定向检索)
        │   └── 分类得分过滤 (min-intent-score: 0.4)
        │   └── 按意图限制检索范围
        │
        └── 后处理链
            ├── DeduplicationPostProcessor (去重)
            └── RerankPostProcessor        (精排重打分)
```

**并行策略：**  
- `CollectionParallelRetriever`：按知识库集合维度并行  
- `IntentParallelRetriever`：按意图维度并行

### 5.3 模型引擎设计

```
用户请求 → ModelSelector (按优先级+健康状态选模型)
              │
              ├── ModelHealthStore (健康状态表：正常/熔断)
              │   └── 失败阈值(failure-threshold: 2) → 熔断
              │   └── 半开探测(open-duration-ms: 30000) → 恢复
              │
              ├── ModelTarget (路由目标: provider + model + url)
              │
              └── ModelRoutingExecutor → 实际调用 LLM API
```

**Provider 适配：**
| Provider | 对话模型 | Embedding | Rerank |
|----------|----------|-----------|--------|
| 百炼 (DashScope) | qwen3-max / qwen-plus | - | qwen3-rerank |
| 硅基流动 (SiliconFlow) | GLM-4.7 | Qwen3-Embedding-8B | - |
| Ollama (本地) | qwen3:8b-fp16 | qwen3-embedding:8b-fp16 | - |

所有调用都通过统一的 `LLMService` / `EmbeddingService` / `RerankService` 接口，按配置的 `default-model` + `candidates` 优先级列表进行路由。

### 5.4 MCP 工具集成

```
用户提问 "查询今天的工单状态"
    │
    ▼
意图分类 → mcp/ticket 意图
    │
    ▼
LLMMcpParameterExtractor (LLM 从对话中提取参数)
    │
    ▼
McpClientToolExecutor (调用远程 MCP Server)
    │
    ▼
结果注入 PromptContext.mcpContext
    │
    ▼
RAGPromptService 将工具结果与检索内容融合
```

### 5.5 会话记忆管理

```
短期记忆：最近 history-keep-turns 轮对话（默认 4 轮）
长期记忆：超过 summary-start-turns 轮（默认 5 轮）触发 LLM 摘要压缩
         summary-max-chars 控制摘要最长字符数（默认 200）
         摘要存储到 conversation_summary 表
```

### 5.6 缓存与并发控制

| 机制 | 实现 | 用途 |
|------|------|------|
| **对话限流** | `@ChatRateLimit` + Redisson 分布式锁 | 全局最多 N 个并发对话 |
| **文档上传信号量** | `UploadRateLimitFilter` + Redisson Semaphore | 限制并发上传数 |
| **定时任务锁** | `ScheduleLockManager` + Redis | 知识库刷新分布式互斥 |
| **幂等消费** | `@IdempotentConsume` AOP + Redis | 消息队列重复消费防护 |
| **幂等提交** | `@IdempotentSubmit` AOP | 防止表单重复提交 |

---

## 6. 数据模型概览

### 6.1 核心表

```
知识库相关:
  knowledge_base          -- 知识库（名称、向量配置、分块策略）
  knowledge_document      -- 文档（S3路径、状态、处理模式）
  knowledge_chunk         -- 文档块（向量嵌入、元数据）
  knowledge_document_chunk_log      -- 分块操作日志
  knowledge_document_schedule       -- 文档定时刷新配置
  knowledge_document_schedule_exec  -- 定时刷新执行记录

对话相关:
  conversation            -- 会话（标题、用户）
  conversation_message    -- 对话消息（角色、内容、类型）
  conversation_summary    -- 对话摘要（压缩后内容、Token统计）

意图相关:
  intent_node             -- 意图节点（树形结构、分类描述、Prompt模板）
  query_term_mapping      -- 术语映射表（同义词/缩写→标准术语）

RAG 链路追踪:
  rag_trace_run           -- 每次 RAG 查询的运行记录
  rag_trace_node          -- 各阶段节点耗时

反馈:
  message_feedback        -- 回答质量反馈（赞/踩/备注）

摄入流水线:
  ingestion_pipeline        -- 流水线定义
  ingestion_pipeline_node   -- 流水线节点配置
  ingestion_task            -- 流水线执行任务
  ingestion_task_node       -- 任务节点执行记录

用户:
  user_info               -- 用户表
```

### 6.2 向量存储

- **pgvector 模式**：`knowledge_chunk` 表包含 `embedding vector(1536)` 列
- **Milvus 模式**：Collection 名通过 `rag.default.collection-name` 配置，维度 1536

---

## 7. 配置要点

```yaml
# 核心配置项速览 (bootstrap/src/main/resources/application.yaml)

server.port: 9090
spring.datasource: PostgreSQL (ragent 库)
spring.data.redis: Redis
rocketmq.name-server: 127.0.0.1:9876
milvus.uri: http://localhost:19530

rag:
  vector.type: pg                    # pg 或 milvus
  query-rewrite.enabled: true        # Query改写开关
  rate-limit.global:                 # 对话并发限制
    enabled: true
    max-concurrent: 1
  memory:                            # 会话记忆
    history-keep-turns: 4            # 保留最近 4 轮
    summary-start-turns: 5           # 第 5 轮开始触发摘要
  mcp.servers:                       # MCP Server 列表
    - name: default
      url: http://localhost:9099

ai:
  providers:                         # AI 模型提供商配置
    bailian/siliconflow/ollama
  selection:
    failure-threshold: 2             # 连续失败 2 次熔断
    open-duration-ms: 30000          # 30 秒后半开探测
  chat.default-model: qwen3-max      # 默认对话模型
  embedding.default-model: qwen-emb-8b
  rerank.default-model: qwen3-rerank
```

---

## 8. 工程规范亮点

1. **异常体系分层**：`ClientException` / `RemoteException` / `ServiceException` + 全局处理器统一拦截
2. **分布式 ID**：`CustomIdentifierGenerator` 基于 Snowflake 的 ID 生成
3. **幂等控制**：AOP 注解 `@IdempotentSubmit` / `@IdempotentConsume`，支持 SpEL 表达式定义 Key
4. **代码风格**：Spotless Maven 插件 + Apache 2.0 License 头自动注入
5. **链路追踪**：`RagTraceContext` + `RagTraceRoot/Node` 记录每次 RAG 查询的完整调用链
6. **流式 SSE**：`SseEmitterSender` 封装，`StreamTaskManager` 管理流式任务生命周期（可中途取消）
7. **RocketMQ 事务消息**：`DelegatingTransactionListener` + `TransactionChecker` 模式保障文档处理可靠性

---

## 9. 从中学到什么（面试/简历视角）

| 话题 | 可说的点 |
|------|----------|
| **RAG 架构** | 6 阶段流水线、多路检索 + Rerank、Query 改写与拆分 |
| **意图识别** | 树形多级分类、LLM 驱动的意图路由、置信度不足的澄清引导 |
| **模型工程** | 多 Provider 统一抽象、健康检查 + 熔断降级、模型路由优先级 |
| **MCP 协议** | 理解 MCP 标准、LLM 自动参数提取、工具与检索融合 |
| **文档处理** | Tika 多格式解析、定长/结构化分块、LLM 增强补充 |
| **向量存储** | pgvector vs Milvus 双实现、向量索引策略 |
| **并发控制** | 分布式信号量限流、RocketMQ 事务消息、幂等消费 |
| **会话记忆** | 短期历史 + 长期摘要双层机制 |
| **前后端** | SSE 流式输出、动态 Prompt 模板 |
| **代码质量** | 多模块拆分、依赖倒置（infra-ai 抽象层）、AOP 横切关注点 |

---

## 10. 启动路径概览

```
1. 基础设施：PostgreSQL + Redis + RocketMQ + (Milvus 可选)
2. MCP Server（可选）：java -jar mcp-server  ← 独立进程
3. 主应用：bootstrap → RagentApplication.main()
4. 前端：cd frontend && npm run dev
5. 对象存储：S3 兼容服务（MinIO/RustFS）
```

---

> 文档生成时间: 2026-05-25 | 基于 Ragent AI v0.0.1-SNAPSHOT 代码分析
