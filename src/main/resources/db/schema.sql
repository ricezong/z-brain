-- ============================================================
-- 智多星知识库系统 (Z-Brain) 数据库初始化脚本
-- PostgreSQL 14+ / pgvector / pg_trgm / zhparser
-- ============================================================

-- ==================== 扩展安装 ====================
CREATE EXTENSION IF NOT EXISTS vector;       -- pgvector 向量扩展
CREATE EXTENSION IF NOT EXISTS pg_trgm;      -- 模糊匹配扩展
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

-- 3. gin 模糊匹配索引
CREATE INDEX IF NOT EXISTS idx_kb_chunk_content_trgm
    ON kb_chunk USING gin (content gin_trgm_ops);

-- 4. 普通索引
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
    kb_id           BIGINT        NOT NULL,
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
    kb_id           BIGINT        NOT NULL,
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

-- ==================== 初始化默认数据 ====================
INSERT INTO kb_prompt_template (kb_id, name, system_prompt, user_prompt, is_default)
VALUES (NULL, '默认通用模板',
'你是智多星知识库助手，请基于以下检索到的知识库内容回答用户问题。
要求：
1. 严格依据提供的知识库内容作答，不得编造。
2. 回答末尾必须使用 [doc_1] [doc_2] 这样的标记标注引用来源，编号对应上下文中的文档编号。
3. 若知识库内容不足以回答问题，请明确告知用户。',
'已知信息：
{context}

用户问题：{question}

请基于以上已知信息回答用户问题，并在回答末尾使用 [doc_x] 标注引用来源。',
TRUE)
ON CONFLICT DO NOTHING;

-- ==================== 维护任务：定时 ANALYZE ====================
-- 业务低峰期执行 ANALYZE 更新统计信息，保障 ivfflat 召回率
-- 建议通过 pg_cron 或外部调度执行：
-- SELECT cron.schedule('zbrain_analyze_kb_chunk', '0 3 * * *', 'ANALYZE kb_chunk;');
