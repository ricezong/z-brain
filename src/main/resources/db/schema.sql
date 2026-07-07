-- ============================================================
-- 智多星知识库系统 (Z-Brain) 数据库初始化脚本
-- PostgreSQL 14+ / pgvector / zhparser
-- ============================================================

-- ==================== 扩展安装 ====================
CREATE EXTENSION IF NOT EXISTS vector;       -- pgvector 向量扩展
CREATE EXTENSION IF NOT EXISTS zhparser;     -- 中文分词扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";  -- UUID 生成

-- ==================== 中文全文检索配置 ====================
-- 使用 zhparser 作为中文分词器
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
    chunk_size      INT           NOT NULL DEFAULT 256,  -- 分块大小（Token 数），默认 256
    status          VARCHAR(16)   DEFAULT 'active',  -- active / archived
    doc_count       INT           DEFAULT 0,
    chunk_count     INT           DEFAULT 0,
    create_by       VARCHAR(64)   DEFAULT 'system',
    update_by       VARCHAR(64),
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  kb_knowledge_base IS '知识库主表';
COMMENT ON COLUMN kb_knowledge_base.id IS '主键 ID';
COMMENT ON COLUMN kb_knowledge_base.name IS '知识库名称';
COMMENT ON COLUMN kb_knowledge_base.category IS '知识库分类';
COMMENT ON COLUMN kb_knowledge_base.prompt_template_id IS '关联提示词模板 ID';
COMMENT ON COLUMN kb_knowledge_base.chunk_size IS '分块大小（Token 数），默认 256';
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
COMMENT ON COLUMN kb_prompt_template.system_prompt IS '系统提示词';
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
    chunk_size      INT,                            -- 分块大小（Token 数），NULL 时使用知识库配置
    status          VARCHAR(32)   DEFAULT 'pending',
    -- pending-待解析 / parsing-解析中 / pending_review-待审核
    -- embedding-向量化中 / success-完成 / failed-失败
    chunk_count     INT           DEFAULT 0,
    parse_progress  INT           DEFAULT 0,    -- 0-100
    error_message   TEXT,
    metadata        JSONB,
    create_by       VARCHAR(64)   DEFAULT 'system',
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  kb_document IS '文档表';
COMMENT ON COLUMN kb_document.status IS '文档状态机：pending/parsing/pending_review/embedding/success/failed';
COMMENT ON COLUMN kb_document.chunk_size IS '分块大小（Token 数），NULL 时使用知识库的 chunk_size';

CREATE INDEX IF NOT EXISTS idx_kb_doc_kbid ON kb_document(kb_id);
CREATE INDEX IF NOT EXISTS idx_kb_doc_status ON kb_document(status);
CREATE INDEX IF NOT EXISTS idx_kb_doc_hash ON kb_document(file_hash);

-- ==================== 分块表（核心表） ====================
CREATE TABLE IF NOT EXISTS kb_chunk (
    id              BIGSERIAL PRIMARY KEY,
    doc_id          BIGINT        NOT NULL,
    kb_id           BIGINT        NOT NULL,
    parent_id       BIGINT,                       -- 父块 ID，子块才有
    chunk_type      VARCHAR(16)   DEFAULT 'child', -- parent / child
    content         TEXT          NOT NULL,
    content_vector  VECTOR(1024),                 -- 仅子块向量化，父块为 NULL
    tsv             TSVECTOR,                     -- 全文检索向量，由触发器维护
    token_count     INT           DEFAULT 0,
    status          VARCHAR(16)   DEFAULT 'draft', -- draft / active
    metadata        JSONB,                        -- 页码、坐标、字符偏移量等
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  kb_chunk IS '分块表（系统核心表）';
COMMENT ON COLUMN kb_chunk.parent_id IS '父块 ID，子块记录父块 ID 用于溯源';
COMMENT ON COLUMN kb_chunk.content_vector IS '向量字段，仅子块向量化，父块为 NULL';
COMMENT ON COLUMN kb_chunk.tsv IS '全文检索向量，由触发器自动维护';
COMMENT ON COLUMN kb_chunk.metadata IS '元数据：页码、坐标、字符偏移量 start_index/end_index';
COMMENT ON COLUMN kb_chunk.status IS '分块状态：draft-草稿，active-激活可检索';

-- 索引策略
-- 1. ivfflat 向量索引（加速相似度检索）
CREATE INDEX IF NOT EXISTS idx_kb_chunk_vector
    ON kb_chunk USING ivfflat (content_vector vector_cosine_ops)
    WITH (lists = 100);

-- 2. gin 全文检索索引
CREATE INDEX IF NOT EXISTS idx_kb_chunk_tsv
    ON kb_chunk USING gin (tsv);

-- 3. 普通索引
CREATE INDEX IF NOT EXISTS idx_kb_chunk_kbid ON kb_chunk(kb_id);
CREATE INDEX IF NOT EXISTS idx_kb_chunk_docid ON kb_chunk(doc_id);
CREATE INDEX IF NOT EXISTS idx_kb_chunk_parentid ON kb_chunk(parent_id);
CREATE INDEX IF NOT EXISTS idx_kb_chunk_status ON kb_chunk(status);
CREATE INDEX IF NOT EXISTS idx_kb_chunk_type ON kb_chunk(chunk_type);

-- ==================== 触发器：自动维护 tsv ====================
-- 当 content 变化时自动更新 tsv，使用中文分词配置
CREATE OR REPLACE FUNCTION kb_chunk_tsv_update() RETURNS trigger AS $$
BEGIN
    NEW.tsv := to_tsvector('zh_cn', COALESCE(NEW.content, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_kb_chunk_tsv ON kb_chunk;
CREATE TRIGGER trg_kb_chunk_tsv
    BEFORE INSERT OR UPDATE OF content ON kb_chunk
    FOR EACH ROW EXECUTE FUNCTION kb_chunk_tsv_update();

-- ==================== 会话表 ====================
CREATE TABLE IF NOT EXISTS kb_chat_session (
    id              VARCHAR(64)   PRIMARY KEY,    -- UUID
    kb_id           BIGINT,                        -- 可为空，空表示全局对话
    title           VARCHAR(255),
    user_id         VARCHAR(64)   DEFAULT 'anonymous',
    message_count   INT           DEFAULT 0,
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE kb_chat_session IS '对话会话表';

CREATE INDEX IF NOT EXISTS idx_kb_session_kbid ON kb_chat_session(kb_id);
CREATE INDEX IF NOT EXISTS idx_kb_session_userid ON kb_chat_session(user_id);

-- ==================== 对话日志表 ====================
CREATE TABLE IF NOT EXISTS kb_chat_log (
    id              BIGSERIAL PRIMARY KEY,
    session_id      VARCHAR(64)   NOT NULL,
    kb_id           BIGINT,                        -- 可为空，空表示全局对话
    user_id         VARCHAR(64),
    query           TEXT          NOT NULL,
    rewritten_query TEXT,                          -- 改写后的 Query
    hyde_answer     TEXT,                          -- HyDE 假设性答案
    answer          TEXT,
    hit_chunk_ids   JSONB,                         -- 命中的分块 ID 列表
    retrieval_info  JSONB,                         -- 检索过程信息（各路召回数、RRF 分数等）
    token_usage     JSONB,                         -- Token 消耗统计
    cost_time_ms    BIGINT,                        -- 总耗时
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  kb_chat_log IS '对话日志表（审计）';
COMMENT ON COLUMN kb_chat_log.hit_chunk_ids IS '命中的分块 ID 列表';
COMMENT ON COLUMN kb_chat_log.retrieval_info IS '检索过程信息：各路召回数、RRF 分数、Rerank 分数';

CREATE INDEX IF NOT EXISTS idx_kb_log_sessionid ON kb_chat_log(session_id);
CREATE INDEX IF NOT EXISTS idx_kb_log_kbid ON kb_chat_log(kb_id);
CREATE INDEX IF NOT EXISTS idx_kb_log_createtime ON kb_chat_log(create_time);

-- ==================== 自动更新 update_time ====================
CREATE OR REPLACE FUNCTION update_update_time() RETURNS trigger AS $$
BEGIN
    NEW.update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOR tbl IN
        SELECT unnest(ARRAY['kb_knowledge_base','kb_prompt_template','kb_document','kb_chunk','kb_chat_session'])
    LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS trg_%I_updatetime ON %I;', tbl, tbl);
        EXECUTE format('CREATE TRIGGER trg_%I_updatetime BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION update_update_time();', tbl, tbl);
    END LOOP;
END $$;

-- ==================== 系统提示词表 ====================
CREATE TABLE IF NOT EXISTS sys_prompt (
    id              BIGSERIAL PRIMARY KEY,
    prompt_key      VARCHAR(64)   NOT NULL UNIQUE,      -- 逻辑键，如 query_rewrite / hyde / keyword_extract / chitchat
    name            VARCHAR(128)  NOT NULL,             -- 展示名称
    description     VARCHAR(512),                       -- 用途说明
    content         TEXT          NOT NULL,             -- 提示词内容（支持 {query} {history} 等占位符）
    is_active       BOOLEAN       DEFAULT TRUE,
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  sys_prompt IS '系统提示词表（统一管理所有硬编码提示词）';
COMMENT ON COLUMN sys_prompt.prompt_key IS '逻辑键：query_rewrite / hyde / keyword_extract / chitchat / no_result';
COMMENT ON COLUMN sys_prompt.content IS '提示词内容，支持 {query} {history} 占位符';

-- ==================== LLM 模型配置表 ====================
-- 支持多模型配置，按 model_type 区分用途（chat / embedding / rerank）
CREATE TABLE IF NOT EXISTS sys_llm_model (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(128)  NOT NULL,              -- 模型配置名称（如 "DeepSeek 对话模型"）
    model_type      VARCHAR(32)   NOT NULL DEFAULT 'chat', -- chat / embedding / rerank
    provider        VARCHAR(64)   NOT NULL DEFAULT 'openai_compatible', -- 提供商标识
    model_name      VARCHAR(128)  NOT NULL,              -- 模型标识（如 deepseek-v4-pro）
    api_key         VARCHAR(256),                        -- API Key
    base_url        VARCHAR(512),                        -- API Base URL
    temperature     DOUBLE PRECISION DEFAULT 0.3,        -- 温度参数
    max_tokens      INT,                                 -- 最大 Token 数
    is_default      BOOLEAN       DEFAULT FALSE,         -- 是否为该类型的默认模型
    is_active       BOOLEAN       DEFAULT TRUE,
    sort_order      INT           DEFAULT 0,
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE  sys_llm_model IS 'LLM 模型配置表';
COMMENT ON COLUMN sys_llm_model.model_type IS '模型类型：chat-对话模型 / embedding-向量模型 / rerank-重排模型';
COMMENT ON COLUMN sys_llm_model.provider IS '提供商：openai_compatible / dashscope / ollama 等';
COMMENT ON COLUMN sys_llm_model.is_default IS '是否为该 model_type 的默认模型（每种类型只能有一个默认）';

CREATE INDEX IF NOT EXISTS idx_sys_llm_type ON sys_llm_model(model_type);
CREATE INDEX IF NOT EXISTS idx_sys_llm_default ON sys_llm_model(model_type, is_default);

-- 为 sys_prompt 和 sys_llm_model 也添加 update_time 触发器
DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOR tbl IN
        SELECT unnest(ARRAY['sys_prompt','sys_llm_model'])
    LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS trg_%I_updatetime ON %I;', tbl, tbl);
        EXECUTE format('CREATE TRIGGER trg_%I_updatetime BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION update_update_time();', tbl, tbl);
    END LOOP;
END $$;

-- ==================== 初始化默认提示词数据 ====================
INSERT INTO sys_prompt (prompt_key, name, description, content) VALUES
('query_rewrite',
 '查询改写提示词',
 '多轮对话指代消解：根据对话历史将用户最新问题改写为独立、完整、清晰的查询',
 '# 角色
你是一个专业的查询改写引擎，负责将多轮对话中的模糊提问改写为独立、完整、可检索的查询。

# 任务
分析对话历史与用户最新问题，输出一个消解了指代关系的独立查询。

# 规则
1. 将代词（如"它""这个""那个""上面的"）替换为对话中提到的具体对象
2. 保持用户原始意图，不得扩展、缩窄或改变语义
3. 补充必要的上下文使查询自包含（脱离对话也能理解）
4. 仅输出改写后的查询文本，不要任何解释、引号或前缀

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
 '单轮对话关键词提取增强：去除停用词，补充同义词，扩展为更适合语义检索的形式',
 '# 角色
你是一个查询关键词扩展引擎，负责将用户查询转化为更适合语义检索的形式。

# 任务
从用户查询中提取核心关键词，并补充同义词和上下位词，扩展为检索友好的查询。

# 规则
1. 去除"的""了""是""在"等无意义停用词
2. 补充相关同义词、近义词或上下位词
3. 保持查询的核心语义不变
4. 输出扩展后的查询文本，不超过 50 字
5. 仅输出查询文本，不要任何解释、引号或前缀

# 输入
原始查询：{query}

# 输出
扩展后的查询：')
ON CONFLICT (prompt_key) DO NOTHING;

INSERT INTO sys_prompt (prompt_key, name, description, content) VALUES
('hyde',
 'HyDE 假设性答案提示词',
 '生成假设性答案用于向量检索增强，包含与问题相关的关键词和概念',
 '# 角色
你是一个假设性答案生成器，为向量检索提供语义增强。

# 任务
针对用户问题，生成一段假设性答案。该答案不要求事实准确，但需包含与问题高度相关的关键词、概念和表述方式，以提升向量检索的召回率。

# 规则
1. 答案长度控制在 100-200 字
2. 尽可能包含问题涉及的专业术语、关键词和概念
3. 使用陈述句式，模拟真实答案的语言风格
4. 仅输出答案内容，不要任何解释、引号或前缀

# 输入
问题：{query}

# 输出
假设性答案：')
ON CONFLICT (prompt_key) DO NOTHING;

INSERT INTO sys_prompt (prompt_key, name, description, content) VALUES
('chitchat',
 '闲聊系统提示词',
 '当用户意图被识别为闲聊时使用的系统提示词',
 '# 角色
你是「智多星」知识库助手，一个友好、专业、高效的 AI 问答伙伴。

# 行为准则
1. 用简洁清晰的语言回答用户的日常问候和闲聊
2. 保持友好、礼貌的态度，展现乐于助人的特质
3. 回答使用 Markdown 格式输出
4. 如果用户的问题涉及知识查询，引导用户使用知识库问答功能

# 自我介绍（当被问到"你是谁"时）
你是智多星知识库助手，可以帮助用户：
- 基于知识库回答专业问题，支持引用溯源
- 多轮对话上下文理解
- 智能检索与精准回答')
ON CONFLICT (prompt_key) DO NOTHING;

INSERT INTO sys_prompt (prompt_key, name, description, content) VALUES
('no_result',
 '无结果提示词',
 '当知识库检索无结果时返回给用户的提示文案',
 '抱歉，知识库中未找到与您问题相关的内容。

您可以尝试：
1. **更换问法** — 重新组织问题，使用更通用的关键词
2. **简化问题** — 拆分为更具体的子问题
3. **联系管理员** — 建议补充相关知识库内容')
ON CONFLICT (prompt_key) DO NOTHING;

-- ==================== 初始化默认提示词模板 ====================
INSERT INTO kb_prompt_template (kb_id, name, system_prompt, user_prompt, is_default)
VALUES (NULL, '默认通用模板',
'# 角色
你是「智多星」知识库助手，专门基于检索到的知识库内容回答用户问题。

# 回答原则
1. **严格依据知识库**：仅基于下方「已知信息」作答，不得编造、臆测或引入外部知识。
2. **引用溯源**：引用了某文档内容的句子，必须在该句末尾内联标注 `[doc_x]`。一个句子引用了多个文档时标注多个，如 `[doc_1][doc_2]`。
3. **诚实告知**：若已知信息不足以回答问题，请明确告知"根据知识库内容，暂未找到相关信息"，不要编造答案。
4. **结构化输出**：回答使用 Markdown 格式，合理使用标题、列表、表格、代码块等元素组织内容，使回答清晰易读。

# 引用标记规则
- 格式：`[doc_1]`、`[doc_2]`，紧跟在引用了该文档的句子末尾
- 示例：根据最新数据，Q3 营收增长了 15%[doc_1]。
- 多文档引用：该产品支持多种部署方式[doc_1][doc_3]。',
'## 已知信息

{context}

## 用户问题

{question}

## 回答要求

请基于以上已知信息回答用户问题。遵循以下规则：
1. 仅使用已知信息中的内容作答，不要编造
2. 引用标记 `[doc_x]` 紧跟在引用了该文档的句子末尾，内联标注
3. 使用 Markdown 格式输出，合理使用标题、列表、表格等
4. 若信息不足，请明确告知',
TRUE)
ON CONFLICT DO NOTHING;

-- ==================== 初始化默认 LLM 模型配置 ====================
INSERT INTO sys_llm_model (name, model_type, provider, model_name, api_key, base_url, temperature, max_tokens, is_default, is_active, sort_order)
VALUES (
    'DeepSeek 对话模型',
    'chat',
    'openai_compatible',
    'deepseek-v4-pro',
    'sk-21c6492284654354a26ff38320875d31',
    'https://api.deepseek.com',
    0.3,
    4096,
    TRUE,
    TRUE,
    0
)
ON CONFLICT DO NOTHING;

-- ==================== 维护任务：定时 ANALYZE ====================
-- 业务低峰期执行 ANALYZE 更新统计信息，保障 ivfflat 召回率
-- 建议通过 pg_cron 或外部调度执行：
-- SELECT cron.schedule('zbrain_analyze_kb_chunk', '0 3 * * *', 'ANALYZE kb_chunk;');
