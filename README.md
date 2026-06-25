# 智多星知识库系统 (Z-Brain)

> 基于 Spring Boot 3.4.5 + Spring AI 1.1.8 + 阿里云百炼平台的企业级 RAG 知识库系统

## 一、项目简介

智多星知识库系统（Z-Brain）是一套完整的企业级 RAG（Retrieval-Augmented Generation）知识库问答解决方案，融合了向量检索、全文检索、模糊匹配三路混合召回 + RRF 融合 + Rerank 精排的先进检索架构，支持父子分块、人工审核工作台、流式问答与引用溯源等核心能力。

## 二、技术架构

| 组件 | 技术选型 | 说明 |
| :--- | :--- | :--- |
| 基础框架 | Spring Boot 3.4.5 + Java 17 + Spring AI 1.1.8 | Spring AI 提供与大模型交互的抽象层 |
| 百炼 SDK | dashscope-sdk-java | 调用阿里云百炼平台的文本向量与重排序模型 |
| 向量模型 | text-embedding-v4 | 百炼平台先进文本向量模型 |
| 排序模型 | qwen3-rerank | 百炼平台重排序模型 |
| ORM 框架 | 原生 MyBatis (全 XML SQL) | 复杂 SQL 可视化与可维护性 |
| 异步调度 | ThreadPoolTaskExecutor (双线程池) | 解析池 + 向量化池，资源隔离 |
| 关系型数据库 | PostgreSQL + pgvector + pg_trgm + zhparser | 向量存储、模糊匹配、中文分词 |
| 缓存与状态 | Redis | 进度轮询、对话上下文、Embedding 缓存 |
| 文档解析 | Apache Tika | 支持 PDF/Word/PPT 等多种格式 |

## 三、核心模块

| 模块 | 核心职责 |
| :--- | :--- |
| 知识库管理 | 多知识库空间隔离，文档增删改查及状态流转 |
| 文档导入 | 接收文件，Tika 解析为纯文本，异步处理 |
| 分块引擎 | 父子分块（父块 1000 Token，子块 200 Token） |
| 人工审核工作台 | 双屏对比，支持合并、拆分、修改、删除 |
| 向量化与存储 | 百炼 SDK 批量向量化，Redis 缓存，写入 PG |
| 查询预处理 | 意图识别、Query 改写、HyDE 增强 |
| 混合检索与重排 | 向量/全文/模糊三路召回 + RRF 融合 + Rerank |
| 问答与溯源 | Token 预算控制、SSE 流式输出、引用标记解析 |
| 对话与日志 | Redis 短期 + PG 长期，全链路审计日志 |

## 四、环境要求

- JDK 17+
- Maven 3.8+
- PostgreSQL 14+（需安装 pgvector、pg_trgm、zhparser 扩展）
- Redis 6+
- 阿里云百炼平台 API Key

## 五、快速开始

### 1. 初始化数据库

```bash
# 登录 PostgreSQL，创建数据库
createdb zbrain

# 执行初始化脚本
psql -d zbrain -f src/main/resources/db/schema.sql
```

### 2. 配置环境变量

```bash
export DASHSCOPE_API_KEY=your_dashscope_api_key
export OPENAI_API_KEY=your_openai_compatible_api_key
export OPENAI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
export PG_HOST=127.0.0.1
export PG_PORT=5432
export PG_DB=zbrain
export PG_USER=postgres
export PG_PASSWORD=your_password
export REDIS_HOST=127.0.0.1
export REDIS_PORT=6379
```

### 3. 编译打包

```bash
mvn clean package -DskipTests
```

### 4. 启动服务

```bash
java -jar target/z-brain-1.0.0.jar
```

### 5. 访问接口文档

启动后访问 Knife4j 接口文档：`http://localhost:8080/api/doc.html`

## 六、核心链路

### 链路一：文档解析与初步分块
1. 前端上传文件，立即返回文档 ID
2. 后台解析线程池异步执行 Tika 解析与文本清洗
3. 按 1000 Token 切分父块，再按 200 Token 切分子块
4. 子块 metadata 记录父块 ID、字符偏移量、页码
5. 文档状态流转为 `pending_review`

### 链路二：人工审核与分块干预
1. 双屏联动对比：左侧原文，右侧分块树
2. 支持内容修正、合并、拆分、删除、调整父子关系
3. PG 触发器自动重建 tsv
4. 审核通过后批量提交 Diff，状态变更为 `embedding`

### 链路三：向量化与索引构建
1. 文档状态变更为 `embedding` 后进入向量化线程池
2. 查询所有 status=draft 的子块
3. 先检查 Redis Embedding 缓存，缺失才调用百炼 SDK
4. 批量更新子块 vector 字段
5. 父子块状态更新为 `active`，文档状态更新为 `success`

### 链路四：查询预处理与意图路由
1. 意图识别：轻量级判断闲聊或知识问答
2. Query 改写：利用小模型改写指代不清的 Query
3. HyDE 增强：生成假设性答案用于向量检索

### 链路五：多路召回与 RRF 融合
1. 三路召回：向量（Top 20）+ 全文（Top 20）+ 模糊（Top 10）
2. RRF 融合：`score = 1 / (k + rank)`，k=60，取 Top 10

### 链路六：重排序与 Token 预算控制
1. Rerank 精排：百炼 qwen3-rerank，取 Top 5
2. 上下文组装：提取 Top 5 子块对应父块内容
3. Token 预算：总预算 8K，预留 2K，剩余 6K 按精排分值填入
4. 降级策略：Rerank 失败则使用 RRF 融合结果

### 链路七：问答生成与引用溯源
1. 动态 Prompt 组装：填充上下文和问题
2. 强制引用标记：要求 LLM 使用 `[doc_x]` 标注引用
3. SSE 流式输出：后端解析标记并映射为前端可点击链接
4. 上下文与日志沉淀：Redis 短期 + PG 长期审计日志

## 七、API 接口一览

| 模块 | 接口 | 方法 | 说明 |
| :--- | :--- | :--- | :--- |
| 知识库 | /api/knowledge-bases | POST | 创建知识库 |
| 知识库 | /api/knowledge-bases | GET | 分页查询 |
| 知识库 | /api/knowledge-bases/{id} | GET/PUT/DELETE | 详情/更新/删除 |
| 文档 | /api/documents/upload | POST | 上传文档 |
| 文档 | /api/documents/{id}/progress | GET | 查询处理进度 |
| 文档 | /api/documents/{id}/review | POST | 提交审核 |
| 文档 | /api/documents/{id}/embed | POST | 触发向量化 |
| 分块 | /api/chunks/document/{docId} | GET | 查询文档分块 |
| 分块 | /api/chunks/merge | POST | 合并分块 |
| 分块 | /api/chunks/split | POST | 拆分分块 |
| 对话 | /api/chat/sync | POST | 同步问答 |
| 对话 | /api/chat/stream | POST | 流式问答（SSE） |
| 提示词 | /api/prompt-templates | POST/GET | 创建/查询 |
| 提示词 | /api/prompt-templates/kb/{kbId} | GET | 按知识库查询 |

## 八、项目结构

```
z-brain/
├── pom.xml
├── README.md
├── src/main/java/com/zbrain/
│   ├── ZBrainApplication.java          # 主启动类
│   ├── config/                         # 配置类
│   │   ├── ThreadPoolConfig.java       # 双线程池
│   │   ├── RedisConfig.java            # Redis
│   │   ├── DashScopeConfig.java        # 百炼 SDK
│   │   ├── ZBrainProperties.java       # 业务配置
│   │   └── WebMvcConfig.java           # Web MVC
│   ├── controller/                     # Controller 层
│   ├── service/                        # Service 层
│   │   └── impl/
│   ├── mapper/                         # Mapper 接口
│   ├── entity/                         # 实体类
│   ├── dto/                            # DTO
│   │   ├── request/
│   │   └── response/
│   ├── enums/                          # 枚举
│   ├── chunk/                          # 分块引擎
│   │   └── impl/
│   ├── parser/                         # 文档解析
│   ├── retrieval/                      # 检索器
│   ├── llm/                            # LLM 服务
│   │   └── impl/
│   ├── cache/                          # 缓存层
│   ├── common/                         # 通用类
│   ├── util/                           # 工具类
│   └── task/                           # 定时任务
├── src/main/resources/
│   ├── application.yml                 # 主配置
│   ├── application-dev.yml             # 开发环境
│   ├── application-prod.yml            # 生产环境
│   ├── logback-spring.xml              # 日志配置
│   ├── mapper/                         # MyBatis XML
│   └── db/schema.sql                   # 数据库脚本
└── src/test/java/com/zbrain/
    └── ZBrainApplicationTests.java
```

## 九、参考资源

- [百炼 SDK (dashscope-sdk-java)](https://github.com/dashscope/dashscope-sdk-java)
- [Spring AI - OpenAI Chat](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)
- [Spring AI - Chat Memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
- [Spring AI - PGVector](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html)
- [高并发场景调用 DashScope Java SDK 最佳实践](https://help.aliyun.com/zh/model-studio/developer-reference/sambert-in-high-concurrency-scenarios)

## 十、License

MIT License
