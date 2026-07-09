# 智多星知识库系统 (Z-Brain)

> 基于 Spring Boot 3.4.5 + Spring AI 1.1.8 + 阿里云百炼平台的企业级 RAG 知识库系统

## 一、项目简介

智多星知识库系统（Z-Brain）是一套完整的企业级 RAG（Retrieval-Augmented Generation）知识库问答解决方案，融合了向量检索、全文检索、模糊匹配三路混合召回 + RRF 融合 + Rerank 精排的先进检索架构，支持父子分块、人工审核工作台、流式问答与引用溯源等核心能力。

系统采用 **数据库驱动的配置管理方案**，所有提示词和 LLM 模型配置均通过数据库统一管理，支持运行时动态修改，无需重启应用。前端提供可视化配置页面，实现提示词和模型的全生命周期管理。

## 二、技术架构

| 组件 | 技术选型 | 说明 |
| :--- | :--- | :--- |
| 基础框架 | Spring Boot 3.4.5 + Java 17 + Spring AI 1.1.8 | Spring AI 提供与大模型交互的抽象层 |
| 前端框架 | Vue 3 + Element Plus + Vite | SPA 单页应用，响应式布局 |
| Markdown 渲染 | markdown-it + highlight.js | 问答输出 Markdown 渲染 + 代码语法高亮 |
| 百炼 SDK | dashscope-sdk-java | 调用阿里云百炼平台的文本向量与重排序模型 |
| 向量模型 | text-embedding-v4 | 百炼平台先进文本向量模型 |
| 排序模型 | qwen3-rerank | 百炼平台重排序模型 |
| 对话模型 | DeepSeek（OpenAI 兼容协议） | 通过数据库配置，支持动态切换 |
| ORM 框架 | 原生 MyBatis (全 XML SQL) | 复杂 SQL 可视化与可维护性 |
| 异步调度 | ThreadPoolTaskExecutor (双线程池) | 解析池 + 向量化池，资源隔离 |
| 关系型数据库 | PostgreSQL + pgvector + pg_trgm + zhparser | 向量存储、模糊匹配、中文分词 |
| 缓存与状态 | Redis | 进度轮询、对话上下文、Embedding 缓存 |
| 文档解析 | Apache Tika + LlamaIndex Cloud | 多格式支持，PDF 优先使用 LlamaIndex Cloud 高质量解析 |
| 接口文档 | Knife4j (Swagger 3) | 自动生成 API 文档 |

## 三、核心模块

| 模块 | 核心职责 |
| :--- | :--- |
| 知识库管理 | 多知识库空间隔离，文档增删改查及状态流转 |
| 文档导入 | 接收文件，Tika / LlamaIndex Cloud 解析为 Markdown，异步处理 |
| 分块引擎 | 双层递归字符分块：父块（1000 Token）+ 子块（300 Token）|
| 人工审核工作台 | 双屏对比，支持合并、拆分、修改、删除、调整父子关系 |
| 向量化与存储 | 百炼 SDK 批量向量化，Redis 缓存，写入 PG |
| 查询预处理 | 意图识别、Query 改写（提示词全部数据库管理）|
| 混合检索与重排 | 向量/全文/模糊三路召回 + RRF 融合 + Rerank |
| 问答与溯源 | Token 预算控制、SSE 流式输出、Markdown 渲染、引用标记解析 |
| 对话与日志 | Redis 短期 + PG 长期，全链路审计日志 |
| **系统配置管理** | **提示词和 LLM 模型配置数据库化管理，运行时动态生效** |

## 四、环境要求

- JDK 17+
- Maven 3.8+
- PostgreSQL 14+（需安装 pgvector、pg_trgm、zhparser 扩展）
- Redis 6+
- 阿里云百炼平台 API Key（用于向量化与重排序）
- OpenAI 兼容的对话模型 API Key（如 DeepSeek，通过数据库配置）

## 五、快速开始

### 1. 初始化数据库

```bash
# 登录 PostgreSQL，创建数据库
createdb zbrain

# 执行初始化脚本（含表结构、索引、触发器、默认提示词和模型配置）
psql -d zbrain -f src/main/resources/db/schema.sql
```

初始化脚本会自动创建以下内容：
- 7 张业务表（知识库、文档、分块、会话、日志等）
- 2 张系统配置表（`sys_prompt` 提示词管理、`sys_llm_model` 模型配置管理）
- 4 条默认系统提示词（query_rewrite / keyword_extract / chitchat / no_result）
- 1 条默认 RAG 提示词模板
- 1 条默认 LLM 模型配置（DeepSeek 对话模型）
- 全部索引、触发器、中文全文检索配置

### 2. 配置环境变量

```bash
# 数据库（生产环境使用环境变量，开发环境在 application-dev.yml 中配置）
export PG_HOST=127.0.0.1
export PG_PORT=5432
export PG_DB=zbrain
export PG_USER=postgres
export PG_PASSWORD=your_password

# Redis
export REDIS_HOST=127.0.0.1
export REDIS_PORT=6379
export REDIS_PASSWORD=your_redis_password

# LlamaIndex Cloud（可选，用于 PDF 高质量解析）
export LLAMA_CLOUD_API_KEY=your_llama_cloud_api_key
```

> **注意**：对话模型（如 DeepSeek）和百炼平台（向量化/重排序）的 API Key 通过数据库 `sys_llm_model` 表和 `application.yml` 中的 `zbrain.dashscope` 配置管理，不再通过环境变量 `OPENAI_API_KEY` 注入。

### 3. 编译打包

```bash
mvn clean package -DskipTests
```

### 4. 启动服务

```bash
java -jar target/z-brain-1.0.0.jar
```

### 5. 访问应用

- **前端页面**：`http://localhost:8080/`
- **接口文档**：`http://localhost:8080/api/doc.html`
- **健康检查**：`http://localhost:8080/api/actuator/health`

## 六、系统配置管理

### 6.1 提示词管理

所有系统级提示词通过 `sys_prompt` 表统一管理，支持运行时修改：

| prompt_key | 名称 | 用途 |
| :--- | :--- | :--- |
| `query_rewrite` | 查询改写 | 多轮对话指代消解，将模糊提问改写为独立查询 |
| `keyword_extract` | 关键词扩展 | 单轮对话关键词提取增强，补充同义词 |
| `chitchat` | 闲聊提示词 | 闲聊场景的系统提示词，要求 Markdown 格式输出 |
| `no_result` | 无结果提示 | 检索无结果时的友好提示文案 |

提示词采用结构化模板（`# 角色` → `# 任务` → `# 规则` → `# 输入` → `# 输出`），支持 `{query}`、`{history}` 等占位符。

### 6.2 LLM 模型配置

通过 `sys_llm_model` 表管理所有 LLM 模型配置：

- 支持 **多模型配置**，按 `model_type` 区分用途（chat / embedding / rerank）
- 支持 **多提供商**（openai_compatible / dashscope / ollama）
- 每种类型支持设置 **默认模型**，修改后自动清除缓存，下次调用即生效
- 前端提供完整的 CRUD 界面

### 6.3 前端配置页面

访问「系统配置」页面（`/system-config`）可进行：
- 查看和编辑所有系统提示词（支持占位符提示）
- 新增、编辑、删除 LLM 模型配置
- 设置默认模型
- 按类型筛选模型

## 七、核心链路

### 链路一：文档解析与递归字符分块
1. 前端上传文件，立即返回文档 ID
2. 后台解析线程池异步执行：PDF 优先使用 LlamaIndex Cloud 解析，其余格式走 Tika
3. 解析输出 Markdown 格式，保留标题、表格等语义结构
4. `RecursiveCharacterSplitter` 父层递归字符切分（1000 Token，重叠 150）
5. `RecursiveCharacterSplitter` 子层递归字符切分（300 Token，重叠 40）
6. 子块 metadata 记录父块 ID、字符偏移量、页码
7. 文档状态流转为 `pending_review`

### 链路二：人工审核与分块干预
1. 双屏联动对比：左侧原文，右侧分块树
2. 支持内容修正、合并、拆分、删除、调整父子关系
3. PG 触发器自动重建 tsv（全文检索向量）
4. 审核通过后批量提交 Diff，状态变更为 `embedding`

### 链路三：向量化与索引构建
1. 文档状态变更为 `embedding` 后进入向量化线程池
2. 查询所有 status=draft 的子块
3. 先检查 Redis Embedding 缓存，缺失才调用百炼 SDK
4. 批量更新子块 vector 字段
5. 父子块状态更新为 `active`，文档状态更新为 `success`

### 链路四：查询预处理与意图路由
1. **意图识别**：轻量级判断闲聊或知识问答（关键词匹配 + 长度/疑问词规则）
2. **Query 改写**：多轮对话时利用 LLM 改写指代不清的 Query（提示词从数据库读取）
3. **关键词扩展**：单轮对话时提取核心关键词并补充同义词（提示词从数据库读取）

### 链路五：多路召回与 RRF 融合
1. 三路召回：向量（Top 20）+ 全文（Top 20）+ 模糊（Top 10）
2. RRF 融合：`score = 1 / (k + rank)`，k=60，取 Top 10

### 链路六：重排序与 Token 预算控制
1. Rerank 精排：百炼 qwen3-rerank，取 Top 5
2. 上下文组装：提取 Top 5 子块对应父块内容
3. Token 预算：总预算 8K，预留 2K，剩余 6K 按精排分值填入
4. 降级策略：Rerank 失败则使用 RRF 融合结果

### 链路七：问答生成与引用溯源
1. 动态 Prompt 组装：从数据库读取提示词模板，填充上下文和问题
2. 强制引用标记：要求 LLM 使用 `[doc_x]` 内联标注引用
3. **Markdown 格式输出**：提示词要求 LLM 使用标题、列表、表格等结构化格式
4. SSE 流式输出：前端实时渲染 Markdown（含代码语法高亮）
5. 引用溯源：`[doc_x]` 标记映射为前端可点击徽章，弹窗展示原文
6. 上下文与日志沉淀：Redis 短期 + PG 长期审计日志

## 八、API 接口一览

### 8.1 业务接口

| 模块 | 接口 | 方法 | 说明 |
| :--- | :--- | :--- | :--- |
| 知识库 | /api/knowledge-bases | POST | 创建知识库 |
| 知识库 | /api/knowledge-bases | GET | 分页查询 |
| 知识库 | /api/knowledge-bases/{id} | GET/PUT/DELETE | 详情/更新/删除 |
| 知识库 | /api/knowledge-bases/categories | GET | 分类列表 |
| 文档 | /api/documents/upload | POST | 上传文档（异步解析） |
| 文档 | /api/documents | GET | 分页查询 |
| 文档 | /api/documents/{id} | GET/DELETE | 详情/删除 |
| 文档 | /api/documents/{id}/progress | GET | 查询处理进度 |
| 文档 | /api/documents/{id}/review | POST | 提交审核（批量 Diff） |
| 文档 | /api/documents/{id}/embed | POST | 触发向量化 |
| 分块 | /api/chunks/document/{docId} | GET | 查询文档分块 |
| 分块 | /api/chunks/{id} | GET/PUT/DELETE | 详情/更新/删除 |
| 分块 | /api/chunks/merge | POST | 合并分块 |
| 分块 | /api/chunks/split | POST | 拆分分块 |
| 分块 | /api/chunks/{chunkId}/parent/{parentId} | PUT | 调整父子关系 |
| 对话 | /api/chat/stream | POST | 流式问答（SSE） |
| 对话 | /api/chat/rewrite | POST | 优化输入，增强提示词 |
| 提示词模板 | /api/prompt-templates | POST/GET | 创建/查询 |
| 提示词模板 | /api/prompt-templates/{id} | GET/PUT/DELETE | 详情/更新/删除 |
| 提示词模板 | /api/prompt-templates/kb/{kbId} | GET | 按知识库查询 |
| 提示词模板 | /api/prompt-templates/default | GET | 获取默认模板 |

### 8.2 系统配置接口

| 接口 | 方法 | 说明 |
| :--- | :--- | :--- |
| /api/system/prompts | GET | 查询所有系统提示词 |
| /api/system/prompts/{id} | GET | 获取提示词详情 |
| /api/system/prompts/{id} | PUT | 更新系统提示词 |
| /api/system/llm-models | GET/POST | 查询所有/创建模型配置 |
| /api/system/llm-models/type/{modelType} | GET | 按类型查询模型列表 |
| /api/system/llm-models/{id} | GET/PUT/DELETE | 详情/更新/删除模型配置 |
| /api/system/llm-models/{id}/default | PUT | 设置默认模型 |

## 九、数据库表结构

| 表名 | 说明 |
| :--- | :--- |
| `kb_knowledge_base` | 知识库主表 |
| `kb_prompt_template` | 提示词模板表（知识库级别的 RAG 提示词） |
| `kb_document` | 文档表（状态机：pending→parsing→pending_review→embedding→success） |
| `kb_chunk` | 分块表（父子分块，含向量字段、全文检索字段、元数据） |
| `kb_chat_session` | 对话会话表 |
| `kb_chat_log` | 对话日志表（全链路审计） |
| `sys_prompt` | 系统提示词表（统一管理所有系统级提示词） |
| `sys_llm_model` | LLM 模型配置表（多模型、多提供商、动态切换） |

## 十、项目结构

```
z-brain/
├── pom.xml
├── README.md
├── frontend/                              # 前端工程
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── api/                           # API 请求层
│       │   ├── chat.js                    # 对话 API
│       │   ├── chunk.js                   # 分块 API
│       │   ├── document.js                # 文档 API
│       │   ├── knowledgeBase.js           # 知识库 API
│       │   ├── promptTemplate.js          # 提示词模板 API
│       │   ├── request.js                 # Axios 封装
│       │   └── system.js                  # 系统配置 API
│       ├── layout/
│       │   └── MainLayout.vue             # 主布局（侧边栏 + 顶栏）
│       ├── router/
│       │   └── index.js                   # 路由配置
│       ├── styles/
│       │   └── index.css                  # 全局样式
│       ├── utils/
│       │   └── format.js                  # 格式化工具
│       └── views/
│           ├── Dashboard.vue              # 工作台
│           ├── KnowledgeBase.vue          # 知识库管理
│           ├── Document.vue               # 文档管理
│           ├── ChunkReview.vue            # 分块审核工作台
│           ├── Chat.vue                   # 智能问答（Markdown 渲染 + 代码高亮）
│           ├── PromptTemplate.vue         # 提示词模板管理
│           └── SystemConfig.vue           # 系统配置（提示词 + 模型管理）
├── src/main/java/cn/kong/zbrain/
│   ├── ZBrainApplication.java             # 主启动类
│   ├── config/                            # 配置类
│   │   ├── ThreadPoolConfig.java          # 双线程池（解析 + 向量化）
│   │   ├── RedisConfig.java               # Redis 配置
│   │   ├── DashScopeConfig.java           # 百炼 SDK 配置
│   │   ├── ZBrainProperties.java          # 业务配置属性
│   │   └── WebMvcConfig.java              # Web MVC 配置
│   ├── controller/                        # Controller 层
│   │   ├── KnowledgeBaseController.java   # 知识库管理
│   │   ├── DocumentController.java        # 文档管理
│   │   ├── ChunkController.java           # 分块管理（审核工作台）
│   │   ├── ChatController.java            # 对话问答
│   │   ├── PromptTemplateController.java  # 提示词模板
│   │   └── SystemConfigController.java    # 系统配置（提示词 + 模型）
│   ├── service/                           # Service 层
│   │   ├── impl/
│   │   │   ├── ChatServiceImpl.java       # 问答核心链路
│   │   │   ├── QueryPreprocessServiceImpl.java  # 查询预处理
│   │   │   ├── HybridRetrievalServiceImpl.java  # 混合检索
│   │   │   ├── RerankServiceImpl.java     # 重排序
│   │   │   ├── EmbeddingServiceImpl.java  # 向量化
│   │   │   ├── EmbeddingTaskServiceImpl.java    # 向量化任务
│   │   │   ├── DocumentServiceImpl.java   # 文档管理
│   │   │   ├── ChunkServiceImpl.java      # 分块管理
│   │   │   ├── KnowledgeBaseServiceImpl.java    # 知识库管理
│   │   │   ├── PromptTemplateServiceImpl.java   # 提示词模板
│   │   │   ├── SysPromptServiceImpl.java  # 系统提示词管理
│   │   │   └── SysLlmModelServiceImpl.java      # LLM 模型配置管理
│   │   └── ...（接口定义）
│   ├── llm/                               # LLM 服务
│   │   ├── LLMService.java               # LLM 对话接口（同步/流式调用）
│   │   ├── LLMModelRegistry.java          # LLM 模型注册中心接口（生命周期管理）
│   │   └── impl/
│   │       └── LLMServiceImpl.java        # 基于 Spring AI + 数据库配置
│   ├── mapper/                            # MyBatis Mapper
│   │   ├── SysPromptMapper.java(.xml)     # 系统提示词
│   │   ├── SysLlmModelMapper.java(.xml)   # LLM 模型配置
│   │   └── ...（业务 Mapper）
│   ├── entity/                            # 实体类
│   │   ├── SysPrompt.java                 # 系统提示词实体
│   │   ├── SysLlmModel.java               # LLM 模型配置实体
│   │   └── ...（业务实体）
│   ├── chunk/                             # 分块引擎
│   │   ├── ChunkingEngine.java            # 分块接口
│   │   └── impl/
│   │       └── DefaultChunkingEngine.java # 默认分块引擎（双层递归字符分块）
│   ├── parser/                            # 文档解析
│   │   ├── DocumentParser.java            # Tika 解析器（多格式）
│   │   ├── LlamaIndexPdfParser.java       # LlamaIndex Cloud PDF 解析
│   │   └── HtmlToMarkdownConverter.java   # HTML → Markdown 转换
│   ├── retrieval/                         # 检索器
│   │   ├── VectorRetriever.java           # 向量检索
│   │   ├── FullTextRetriever.java         # 全文检索
│   │   └── RRFFusion.java                 # RRF 融合
│   ├── cache/                             # 缓存层
│   │   ├── ChatContextCache.java          # 对话上下文缓存
│   │   ├── DocumentProgressCache.java     # 文档进度缓存
│   │   └── EmbeddingCache.java            # Embedding 缓存
│   ├── common/                            # 通用类
│   │   ├── Result.java                    # 统一响应
│   │   ├── PageResult.java                # 分页结果
│   │   ├── BusinessException.java         # 业务异常
│   │   └── GlobalExceptionHandler.java    # 全局异常处理
│   ├── dto/                               # DTO
│   │   ├── request/                       # 请求 DTO
│   │   └── response/                      # 响应 DTO
│   ├── enums/                             # 枚举
│   ├── util/                              # 工具类
│   │   ├── TokenUtils.java                # Token 计数
│   │   ├── RecursiveCharacterSplitter.java # 递归字符分块器
│   │   └── CommonUtils.java               # 通用工具
│   └── task/
│       └── MaintenanceTask.java           # 定时维护任务
├── src/main/resources/
│   ├── application.yml                    # 主配置
│   ├── application-dev.yml                # 开发环境
│   ├── application-prod.yml               # 生产环境
│   ├── mapper/                            # MyBatis XML
│   │   ├── SysPromptMapper.xml
│   │   ├── SysLlmModelMapper.xml
│   │   └── ...（业务 XML）
│   └── db/schema.sql                      # 数据库初始化脚本
└── src/test/java/
    └── ZBrainApplicationTests.java
```

## 十一、配置说明

### 11.1 主配置 (`application.yml`)

| 配置项 | 说明 |
| :--- | :--- |
| `zbrain.document.*` | 文档上传目录、解析目录、允许的文件类型 |
| `zbrain.chunk.*` | 分块策略：父块 512-2048 Token，子块 256 Token，重叠 32 |
| `zbrain.retrieval.*` | 检索参数：向量 Top 20、全文 Top 20、RRF k=60、Rerank Top 5 |
| `zbrain.query-preprocess.*` | 查询预处理开关：Query 改写 |
| `zbrain.llm.*` | LLM 上下文预算：总 8K Token，预留 2K |
| `zbrain.cache.*` | Redis Key 前缀与 TTL |
| `zbrain.dashscope.*` | 百炼 SDK 配置（API Key、模型名称、批量大小、超时、重试） |
| `zbrain.llama-index.*` | LlamaIndex Cloud 配置（PDF 高质量解析） |

### 11.2 环境配置

- **开发环境** (`application-dev.yml`)：直连数据库和 Redis，DEBUG 日志级别
- **生产环境** (`application-prod.yml`)：通过环境变量注入连接信息，INFO 日志级别

### 11.3 LLM 模型配置（数据库管理）

对话模型配置已从 `application.yml` 迁移至数据库 `sys_llm_model` 表，通过前端「系统配置」页面管理：

- 支持多模型配置（chat / embedding / rerank）
- 支持多提供商（openai_compatible / dashscope / ollama）
- 修改后自动清除 LLM 服务缓存，下次调用即生效
- `application.yml` 中仅保留 `spring.ai.openai.api-key: placeholder` 用于 pgvector 自动配置

## 十二、参考资源

- [Spring AI - OpenAI Chat](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)
- [Spring AI - PGVector](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html)
- [百炼 SDK (dashscope-sdk-java)](https://github.com/dashscope/dashscope-sdk-java)
- [LlamaIndex Cloud Parsing](https://docs.cloud.llamaindex.ai/)
- [pgvector - GitHub](https://github.com/pgvector/pgvector)
- [zhparser - 中文分词](https://github.com/jaiminpan/pg_jieba)
- [高并发场景调用 DashScope Java SDK 最佳实践](https://help.aliyun.com/zh/model-studio/developer-reference/sambert-in-high-concurrency-scenarios)

## 十三、License

MIT License
