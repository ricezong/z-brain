-- ============================================================
-- 元 Eon 数据库初始化脚本（新工程设计方案 N0）
-- PostgreSQL 14+ / pgvector HNSW / zhparser
-- 安全约束：密钥不进 SQL，走环境变量注入
-- ============================================================

-- ==================== 扩展安装 ====================
CREATE EXTENSION IF NOT EXISTS vector;       -- pgvector 向量扩展
CREATE EXTENSION IF NOT EXISTS zhparser;     -- 中文分词扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";  -- UUID 生成

-- ==================== 中文全文检索配置 ====================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_ts_config WHERE cfgname = 'zh_cn') THEN
        CREATE TEXT SEARCH CONFIGURATION zh_cn (PARSER = zhparser);
        ALTER TEXT SEARCH CONFIGURATION zh_cn
            ADD MAPPING FOR n,v,a,i,e,l WITH simple;
    END IF;
END $$;

-- ==================== 知识库表 ====================
CREATE TABLE IF NOT EXISTS kb_knowledge_base (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(128)  NOT NULL,
    description     VARCHAR(512),
    category        VARCHAR(64)   DEFAULT 'general',
    prompt_template_id BIGINT,
    chunk_size      INT           NOT NULL DEFAULT 300,
    status          VARCHAR(16)   DEFAULT 'active',
    doc_count       INT           DEFAULT 0,
    chunk_count     INT           DEFAULT 0,
    create_by       VARCHAR(64)   DEFAULT 'system',
    update_by       VARCHAR(64),
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  kb_knowledge_base IS '知识库主表';
COMMENT ON COLUMN kb_knowledge_base.chunk_size IS '子块分块大小（Token 数），默认 300';
COMMENT ON COLUMN kb_knowledge_base.status IS '状态：active-启用，archived-归档';

CREATE INDEX IF NOT EXISTS idx_kb_kb_status ON kb_knowledge_base(status);
CREATE INDEX IF NOT EXISTS idx_kb_kb_category ON kb_knowledge_base(category);

-- ==================== 提示词模板表 ====================
CREATE TABLE IF NOT EXISTS kb_prompt_template (
    id              BIGSERIAL PRIMARY KEY,
    kb_id           BIGINT,
    name            VARCHAR(128)  NOT NULL,
    system_prompt   TEXT,
    user_prompt     TEXT,
    is_default      BOOLEAN       DEFAULT FALSE,
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  kb_prompt_template IS '提示词模板表';
COMMENT ON COLUMN kb_prompt_template.user_prompt IS '用户提示词模板，支持 {context} {question} 占位符';
CREATE INDEX IF NOT EXISTS idx_kb_pt_kbid ON kb_prompt_template(kb_id);

-- ==================== 文档表 ====================
CREATE TABLE IF NOT EXISTS kb_document (
    id              BIGSERIAL PRIMARY KEY,
    kb_id           BIGINT        NOT NULL,
    file_name       VARCHAR(255)  NOT NULL,
    file_path       VARCHAR(512),
    file_size       BIGINT,
    file_type       VARCHAR(32),
    file_hash       VARCHAR(64),
    chunk_size      INT,
    parse_type      VARCHAR(32)   DEFAULT 'tika',
    status          VARCHAR(32)   DEFAULT 'pending',
    chunk_count     INT           DEFAULT 0,
    parse_progress  INT           DEFAULT 0,
    error_message   TEXT,
    metadata        JSONB,
    create_by       VARCHAR(64)   DEFAULT 'system',
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  kb_document IS '文档表';
COMMENT ON COLUMN kb_document.status IS '文档状态机：pending/parsing/pending_review/embedding/success/failed';
COMMENT ON COLUMN kb_document.file_hash IS '文件 SHA-256 哈希（流式计算，查重拦截重复上传）';

CREATE INDEX IF NOT EXISTS idx_kb_doc_kbid ON kb_document(kb_id);
CREATE INDEX IF NOT EXISTS idx_kb_doc_status ON kb_document(status);
CREATE INDEX IF NOT EXISTS idx_kb_doc_hash ON kb_document(file_hash);

-- ==================== 分块表（核心表） ====================
CREATE TABLE IF NOT EXISTS kb_chunk (
    id              BIGSERIAL PRIMARY KEY,
    doc_id          BIGINT        NOT NULL,
    kb_id           BIGINT        NOT NULL,
    parent_id       BIGINT,
    chunk_type      VARCHAR(16)   DEFAULT 'child',
    content         TEXT          NOT NULL,
    content_vector  VECTOR(1024),
    content_hash    VARCHAR(64),                     -- ★ 内容哈希：UPDATE content 时自动失效向量
    tsv             TSVECTOR,
    token_count     INT           DEFAULT 0,
    status          VARCHAR(16)   DEFAULT 'draft',
    metadata        JSONB,
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  kb_chunk IS '分块表（系统核心表）';
COMMENT ON COLUMN kb_chunk.content_vector IS '向量字段，仅子块向量化，父块为 NULL';
COMMENT ON COLUMN kb_chunk.content_hash IS '内容哈希：content 变更时触发器自动置空 content_vector 并回退 status=draft';
COMMENT ON COLUMN kb_chunk.tsv IS '全文检索向量，由触发器自动维护';
COMMENT ON COLUMN kb_chunk.status IS '分块状态：draft-草稿，active-激活可检索';

-- ★ 索引策略：HNSW 替代 ivfflat（免 probes 调参，recall 更稳）
CREATE INDEX IF NOT EXISTS idx_kb_chunk_vector
    ON kb_chunk USING hnsw (content_vector vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE INDEX IF NOT EXISTS idx_kb_chunk_tsv
    ON kb_chunk USING gin (tsv);

CREATE INDEX IF NOT EXISTS idx_kb_chunk_kbid ON kb_chunk(kb_id);
CREATE INDEX IF NOT EXISTS idx_kb_chunk_docid ON kb_chunk(doc_id);
CREATE INDEX IF NOT EXISTS idx_kb_chunk_parentid ON kb_chunk(parent_id);
CREATE INDEX IF NOT EXISTS idx_kb_chunk_status ON kb_chunk(status);
CREATE INDEX IF NOT EXISTS idx_kb_chunk_type ON kb_chunk(chunk_type);

-- ==================== 触发器：自动维护 tsv + content 变更失效向量 ====================
CREATE OR REPLACE FUNCTION kb_chunk_tsv_update() RETURNS trigger AS $$
BEGIN
    NEW.tsv := to_tsvector('zh_cn', COALESCE(NEW.content, ''));
    -- ★ content 变更时自动失效向量（根治旧向量被检索的正确性 bug）
    IF (TG_OP = 'UPDATE') AND (OLD.content IS DISTINCT FROM NEW.content) THEN
        NEW.content_vector := NULL;
        NEW.status := 'draft';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_kb_chunk_tsv ON kb_chunk;
CREATE TRIGGER trg_kb_chunk_tsv
    BEFORE INSERT OR UPDATE OF content ON kb_chunk
    FOR EACH ROW EXECUTE FUNCTION kb_chunk_tsv_update();

-- ==================== 会话表 ====================
CREATE TABLE IF NOT EXISTS chat_session (
    id              VARCHAR(64)   PRIMARY KEY,
    kb_id           BIGINT,
    title           VARCHAR(255),
    user_id         VARCHAR(64)   DEFAULT 'anonymous',
    message_count   INT           DEFAULT 0,
    mode            VARCHAR(16)   DEFAULT 'agent',
    current_task_id VARCHAR(64),
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  chat_session IS '对话会话表';
COMMENT ON COLUMN chat_session.mode IS '会话模式：agent-个人助手（新工程唯一模式）';
CREATE INDEX IF NOT EXISTS idx_chat_session_kbid ON chat_session(kb_id);
CREATE INDEX IF NOT EXISTS idx_chat_session_userid ON chat_session(user_id);

-- ==================== 会话消息表（ChatMemory 持久化） ====================
CREATE TABLE IF NOT EXISTS chat_message (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(64)   NOT NULL,
    type            VARCHAR(16)   NOT NULL,
    content         TEXT          NOT NULL,
    msg_time        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  chat_message IS 'Agent 会话消息表（ChatMemory 持久化，压缩 Advisor 就地改写历史内容）';
COMMENT ON COLUMN chat_message.content IS '消息序列化 JSON：text / tool_calls / usage / metadata（msg_seq、压缩 tier 等）';
CREATE INDEX IF NOT EXISTS idx_chat_msg_conv ON chat_message(conversation_id, id);

-- ==================== 检索审计表（拆三表之一，替代旧 chat_log 聚合行） ====================
CREATE TABLE IF NOT EXISTS retrieval_trace (
    id              BIGSERIAL PRIMARY KEY,
    session_id      VARCHAR(64)   NOT NULL,
    query           TEXT          NOT NULL,
    rewritten_query TEXT,
    vector_count    INT,
    fulltext_count  INT,
    rrf_count       INT,
    rerank_count    INT,
    rerank_used     BOOLEAN       DEFAULT TRUE,
    cost_time_ms    BIGINT,
    trace           JSONB,                           -- 各路命中数/RRF分/rerank分/耗时明细
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE retrieval_trace IS '检索审计表（替代旧 chat_log.retrieval_info 聚合字段）';
CREATE INDEX IF NOT EXISTS idx_rt_session ON retrieval_trace(session_id);

-- ==================== 引用映射表（拆三表之二） ====================
CREATE TABLE IF NOT EXISTS citation_ref (
    id              BIGSERIAL PRIMARY KEY,
    session_id      VARCHAR(64)   NOT NULL,
    label           VARCHAR(32)   NOT NULL,          -- doc_1, doc_2 ...
    chunk_id        BIGINT        NOT NULL,
    doc_id          BIGINT,
    doc_name        VARCHAR(255),
    snippet         TEXT,                            -- 子块内容摘要（≤300字符）
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE citation_ref IS '引用映射表（citation 不存 fullContent，前端按 chunkId 回查）';
CREATE INDEX IF NOT EXISTS idx_cr_session ON citation_ref(session_id);

-- ==================== Agent 工具调用审计表（拆三表之三） ====================
CREATE TABLE IF NOT EXISTS tool_trace (
    id              BIGSERIAL PRIMARY KEY,
    session_id      VARCHAR(64)   NOT NULL,
    tool_name       VARCHAR(64)   NOT NULL,
    reason          TEXT,
    status          VARCHAR(16)   NOT NULL,          -- SUCCESS / FAILED
    args_json       TEXT,
    result_summary  TEXT,                            -- 结果摘要（超长部分外置 artifact）
    duration_ms     BIGINT,
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE tool_trace IS 'Agent 工具调用审计表';
CREATE INDEX IF NOT EXISTS idx_tt_session ON tool_trace(session_id);

-- ==================== 上下文压缩增量摘要链 ====================
CREATE TABLE IF NOT EXISTS agent_context_summary (
    id              BIGSERIAL PRIMARY KEY,
    session_id      VARCHAR(64)   NOT NULL,
    summary         TEXT          NOT NULL,
    base_message_id BIGINT,
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE agent_context_summary IS '上下文压缩增量摘要链';
CREATE INDEX IF NOT EXISTS idx_acs_session ON agent_context_summary(session_id, id);

-- ==================== 压缩决策持久副本 ====================
CREATE TABLE IF NOT EXISTS agent_compression_decision (
    message_id      BIGINT        PRIMARY KEY,
    session_id      VARCHAR(64)   NOT NULL,
    tier            VARCHAR(8)    NOT NULL,
    artifact_uri    VARCHAR(512),
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  agent_compression_decision IS '压缩决策持久副本（Redis 为主）';

-- ==================== 任务 Checkpoint ====================
CREATE TABLE IF NOT EXISTS agent_task_checkpoint (
    task_id         VARCHAR(64)   PRIMARY KEY,
    session_id      VARCHAR(64)   NOT NULL,
    state           VARCHAR(32)   NOT NULL,
    summary         TEXT,
    artifacts       JSONB,
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE agent_task_checkpoint IS 'Agent 任务状态机断点';
CREATE INDEX IF NOT EXISTS idx_atc_session ON agent_task_checkpoint(session_id);

-- ==================== 踩坑记录本 ====================
CREATE TABLE IF NOT EXISTS agent_pitfall_log (
    id              BIGSERIAL PRIMARY KEY,
    happen_date     DATE          NOT NULL,
    one_line_error  VARCHAR(255)  NOT NULL,
    correct_action  VARCHAR(512)  NOT NULL,
    escalate_count  INT           DEFAULT 0,
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE agent_pitfall_log IS '踩坑记录本';

-- ==================== 自动更新 update_time ====================
CREATE OR REPLACE FUNCTION update_update_time() RETURNS trigger AS $$
BEGIN
    NEW.update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE tbl TEXT;
BEGIN
    FOR tbl IN
        SELECT unnest(ARRAY['kb_knowledge_base','kb_prompt_template','kb_document','kb_chunk','chat_session',
                            'agent_context_summary','agent_compression_decision','agent_task_checkpoint','agent_pitfall_log',
                            'sys_prompt','sys_llm_model'])
    LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS trg_%I_updatetime ON %I;', tbl, tbl);
        EXECUTE format('CREATE TRIGGER trg_%I_updatetime BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION update_update_time();', tbl, tbl);
    END LOOP;
END $$;

-- ==================== 系统提示词表 ====================
CREATE TABLE IF NOT EXISTS sys_prompt (
    id              BIGSERIAL PRIMARY KEY,
    prompt_key      VARCHAR(64)   NOT NULL UNIQUE,
    name            VARCHAR(128)  NOT NULL,
    description     VARCHAR(512),
    content         TEXT          NOT NULL,
    is_active       BOOLEAN       DEFAULT TRUE,
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  sys_prompt IS '系统提示词表（统一管理所有硬编码提示词）';

-- ==================== LLM 模型配置表 ====================
CREATE TABLE IF NOT EXISTS sys_llm_model (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(128)  NOT NULL,
    model_type      VARCHAR(32)   NOT NULL DEFAULT 'chat',
    provider        VARCHAR(64)   NOT NULL DEFAULT 'openai_compatible',
    model_name      VARCHAR(128)  NOT NULL,
    api_key         VARCHAR(256),                        -- ★ 密钥由环境变量注入，不写入 SQL
    base_url        VARCHAR(512),
    temperature     DOUBLE PRECISION DEFAULT 0.3,
    max_tokens      INT,
    is_default      BOOLEAN       DEFAULT FALSE,
    is_active       BOOLEAN       DEFAULT TRUE,
    sort_order      INT           DEFAULT 0,
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  sys_llm_model IS 'LLM 模型配置表';
COMMENT ON COLUMN sys_llm_model.model_type IS '模型类型：chat-主对话模型 / chat_light-辅助模型(摘要/复盘) / embedding-向量模型 / rerank-重排模型';
COMMENT ON COLUMN sys_llm_model.api_key IS 'API Key（运行时由环境变量覆盖，SQL 初始化不写明文密钥）';
CREATE INDEX IF NOT EXISTS idx_sys_llm_type ON sys_llm_model(model_type);
CREATE INDEX IF NOT EXISTS idx_sys_llm_default ON sys_llm_model(model_type, is_default);

-- ==================== 外部 API 配置表 ====================
CREATE TABLE IF NOT EXISTS sys_api_config (
    id            BIGSERIAL    PRIMARY KEY,
    config_type   VARCHAR(32)  NOT NULL UNIQUE,
    enabled       BOOLEAN      DEFAULT TRUE,
    api_key       VARCHAR(256),
    base_url      VARCHAR(512),
    config        JSONB
);
COMMENT ON TABLE sys_api_config IS '外部 API 配置表';
COMMENT ON COLUMN sys_api_config.api_key IS 'API Key（运行时由环境变量覆盖，SQL 初始化不写明文密钥）';

-- ==================== 初始化默认提示词数据 ====================
INSERT INTO sys_prompt (prompt_key, name, description, content) VALUES
('query_rewrite',
 '查询改写提示词',
 '多轮对话指代消解',
 '# 角色
你是一个专业的查询改写引擎，负责将多轮对话中的模糊提问改写为独立、完整、可检索的查询。

# 任务
分析对话历史与用户最新问题，输出一个消解了指代关系的独立查询。

# 规则
1. 将代词替换为对话中提到的具体对象
2. 保持用户原始意图，不得扩展、缩窄或改变语义
3. 补充必要的上下文使查询自包含
4. 使用中文输出
5. 仅输出改写后的查询文本

# 输入
对话历史：
{history}

最新问题：{query}

# 输出
改写后的查询：')
ON CONFLICT (prompt_key) DO NOTHING;

INSERT INTO sys_prompt (prompt_key, name, description, content) VALUES
('keyword_extract',
 '关键词扩展提示词',
 '单轮对话关键词提取增强',
 '# 角色
你是一个查询关键词扩展引擎。

# 任务
从用户查询中提取核心关键词，并补充同义词和上下位词。

# 规则
1. 去除停用词
2. 补充相关同义词、近义词
3. 使用中文输出，不超过 50 字
4. 仅输出查询文本

# 输入
原始查询：{query}

# 输出
扩展后的查询：')
ON CONFLICT (prompt_key) DO NOTHING;

INSERT INTO sys_prompt (prompt_key, name, description, content) VALUES
('no_result',
 '无结果提示词',
 '检索无结果时返回给用户的提示',
 '抱歉，知识库中未找到与您问题相关的内容。

您可以尝试：
1. 更换问法
2. 简化问题
3. 联系管理员补充知识库内容')
ON CONFLICT (prompt_key) DO NOTHING;

INSERT INTO sys_prompt (prompt_key, name, description, content) VALUES
('agent_system',
 'Agent 主系统提示词',
 '个人助手主系统提示词的硬约束层（人格/用户画像/记忆由 workspace md 资产动态注入，本层放不可违反的规则）',
 '# 角色
你是「元」，用户的个人 AI 助手。人格与行为风格以注入的 SOUL.md 为准，用户偏好以 USER.md 为准，长期记忆以 MEMORY.md 为准。

# 硬规则（不可违反）
1. 知识盲区必须调用工具查询，禁止编造；你非常欠缺实时信息与私有数据，必须多用工具
2. 调用工具时必须填写 reason 参数，说明调用动机
3. 涉及危险操作（写文件/发消息/执行命令）前，必须先复述目标、动作、风险，获得批准
4. 上下文中的 artifact:// 引用可用回取工具获取完整内容
5. 使用中文，Markdown 格式输出

# 能力
你可以使用的工具与 Skill 索引见下方注入内容，按需调用。')
ON CONFLICT (prompt_key) DO NOTHING;

INSERT INTO sys_prompt (prompt_key, name, description, content) VALUES
('compact_summary',
 '上下文增量压缩提示词',
 '四级水位线 COMPACT 阶段：将上次摘要与新增对话合并为新摘要，防"摘要的摘要"语义漂移',
 '# 角色
你是上下文压缩引擎，负责将长对话历史压缩为高密度摘要。

# 任务
将【上次摘要】与【新增对话】合并为一份新摘要。新摘要必须自包含，脱离原始对话也能让读者完全理解任务状态。

# 输出结构（严格遵守，不得增减章节）
## 原始目标
用户最初要达成的目标（一句话，始终保留最初表述）
## 分阶段进展
已完成的关键步骤与结论（按时间序，保留具体数值与结果）
## 涉及的文件与 ID
所有出现过的文件路径、文档 ID、任务 ID、artifact 引用（保留具体值，禁止泛化）
## 待办
未完成事项与下一步计划
## 已放弃的路径
尝试过但被否决的方案及原因（防止重复犯错）

# 规则
1. 保留所有具体值：数字、路径、ID、错误信息原文
2. 删除客套话、重复表述、已解决的小问题细节
3. 若上次摘要与新增对话冲突，以新增对话为准并注明变更
4. 总长度控制在 1500 字以内

# 输入
【上次摘要】
{previous_summary}

【新增对话】
{delta}')
ON CONFLICT (prompt_key) DO NOTHING;

INSERT INTO sys_prompt (prompt_key, name, description, content) VALUES
('memory_review',
 '记忆提炼复盘提示词',
 'Nudge Engine 后台 Review：从近期对话中提炼值得长期记住的声明式事实',
 '# 角色
你是记忆提炼引擎，静默审查对话片段，判断是否有值得写入长期记忆的事实。

# 任务
阅读对话记录，提炼 0-N 条值得长期记住的**声明式事实**（用户偏好、项目事实、账号路径、稳定习惯），不记录命令式指令、临时上下文、客套话。

# 规则
1. 只写事实，不写"应该怎么做"的指令（那是 Skill 的职责）
2. 每条一行，含信息来源日期
3. 与已有记忆重复或仅细微更新的，输出合并建议而非新增
4. 没东西值得存时，只回复：Nothing to save

# 已有长期记忆
{current_memory}

# 近期对话
{transcript}

# 输出
逐行输出事实清单，或 Nothing to save')
ON CONFLICT (prompt_key) DO NOTHING;

INSERT INTO sys_prompt (prompt_key, name, description, content) VALUES
('skill_review',
 'Skill 沉淀复盘提示词',
 'Nudge Engine 后台 Review：判断近期工具使用是否值得沉淀为 Skill（SOP）',
 '# 角色
你是 Skill 沉淀引擎，静默审查工具调用记录，判断是否值得沉淀可复用的操作流程。

# 触发条件（满足其一才沉淀）
1. 同类任务重复出现 5 次以上且流程稳定
2. 刚修复了一个踩坑，值得记录正确做法
3. 被用户纠正过做法，需要固化正确流程

# 输出格式（SKILL.md 草稿）
---
name: 技能名（英文小写连字符）
description: 一句话说明何时使用
---
# 技能名
## When to use
## Steps
## Pitfalls

# 规则
1. 步骤必须可复现，含具体命令/工具名/参数
2. Pitfalls 记录本次踩过的坑
3. 不满足触发条件时，只回复：Nothing to save

# 近期工具调用记录
{tool_trace}')
ON CONFLICT (prompt_key) DO NOTHING;

INSERT INTO sys_prompt (prompt_key, name, description, content) VALUES
('observation_wrap',
 '工具结果语义标注提示词',
 '将原始工具输出整理为带固定字段的语义标注（action/reason/status/scope/fullContent）',
 '# 角色
你是工具结果整理器，将原始工具输出压缩为语义标注。

# 输出结构
### Observation
- action: 执行了什么（工具名+关键参数）
- reason: 为什么执行（取自工具调用的 reason 参数）
- status: SUCCESS / FAILED / PARTIAL
- scope: 结果覆盖了什么、未覆盖什么
- fullContent: 完整内容位置（inline 或 artifact:// 路径）
- summary: 结果关键信息摘要（保留具体值，200 字内）

# 输入
工具名：{tool_name}
调用原因：{reason}
原始输出：
{raw_output}')
ON CONFLICT (prompt_key) DO NOTHING;

-- ==================== 初始化默认提示词模板 ====================
INSERT INTO kb_prompt_template (kb_id, name, system_prompt, user_prompt, is_default)
VALUES (NULL, '默认通用模板',
'# 角色
你是「元」知识库助手，专门基于检索到的知识库内容回答用户问题。

# 回答原则
1. 严格依据知识库：仅基于下方「已知信息」作答，不得编造
2. 引用溯源：引用了某文档内容的句子，必须在该句末尾内联标注 [doc_x]
3. 诚实告知：若已知信息不足，明确告知
4. 结构化输出：Markdown 格式
5. 中文回答

# 引用标记规则
- 格式：[doc_1]、[doc_2]，紧跟在引用句末尾
- 已知信息中的 [doc_x] 为文档编号标识。',
'## 已知信息

{context}

## 用户问题

{question}

请基于以上已知信息回答用户问题。',
TRUE)
ON CONFLICT DO NOTHING;

-- ==================== 初始化默认 LLM 模型配置（密钥由环境变量注入） ====================
-- ★ 安全底线：schema.sql 只建表 + 非密钥数据；apiKey 留空，运行时由环境变量覆盖
INSERT INTO sys_llm_model (name, model_type, provider, model_name, api_key, base_url, temperature, max_tokens, is_default, is_active, sort_order)
VALUES (
    'DeepSeek 对话模型', 'chat', 'openai_compatible', 'deepseek-v4-pro',
    NULL, 'https://api.deepseek.com', 0.3, 4096, TRUE, TRUE, 0
) ON CONFLICT DO NOTHING;

INSERT INTO sys_llm_model (name, model_type, provider, model_name, api_key, base_url, is_default, is_active, sort_order)
VALUES (
    '百炼向量模型', 'embedding', 'dashscope', 'text-embedding-v4',
    NULL, 'https://dashscope.aliyuncs.com/api/v1', TRUE, TRUE, 0
) ON CONFLICT DO NOTHING;

INSERT INTO sys_llm_model (name, model_type, provider, model_name, api_key, base_url, is_default, is_active, sort_order)
VALUES (
    '百炼重排模型', 'rerank', 'dashscope', 'qwen3-rerank',
    NULL, 'https://dashscope.aliyuncs.com/api/v1', TRUE, TRUE, 0
) ON CONFLICT DO NOTHING;

INSERT INTO sys_llm_model (name, model_type, provider, model_name, api_key, base_url, temperature, max_tokens, is_default, is_active, sort_order)
VALUES (
    'XiaoMi 对话模型', 'chat', 'openai_compatible', 'mimo-v2.5-pro',
    NULL, 'https://token-plan-cn.xiaomimimo.com', 0.3, 4096, FALSE, TRUE, 0
) ON CONFLICT DO NOTHING;

-- ==================== 初始化外部 API 配置（密钥由环境变量注入） ====================
INSERT INTO sys_api_config (config_type, enabled, api_key, base_url, config)
SELECT 'llama_index', TRUE, NULL, 'https://api.cloud.llamaindex.ai', '{"tier":"AGENTIC"}'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM sys_api_config WHERE config_type = 'llama_index');

-- ==================== 维护任务：定时 ANALYZE ====================
-- 业务低峰期执行 ANALYZE 更新统计信息，保障 HNSW 召回率
-- 建议通过 pg_cron 或外部调度执行：
-- SELECT cron.schedule('eon_analyze_kb_chunk', '0 3 * * *', 'ANALYZE kb_chunk;');
