package cn.kong.zbrain.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Z-Brain 业务配置
 *
 * <p>集中管理分块策略、检索参数、LLM 上下文预算、缓存 TTL 等业务参数。</p>
 *
 * @author zbrain-team
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "zbrain")
public class ZBrainProperties {

    /**
     * 文档相关配置
     */
    private Document document = new Document();

    /**
     * 分块策略配置
     */
    private Chunk chunk = new Chunk();

    /**
     * 检索相关配置
     */
    private Retrieval retrieval = new Retrieval();

    /**
     * LLM 上下文预算配置
     */
    private Llm llm = new Llm();

    /**
     * 缓存配置
     */
    private Cache cache = new Cache();

    /**
     * 查询预处理配置（系统级控制，不对用户暴露）
     */
    private QueryPreprocess queryPreprocess = new QueryPreprocess();

    @Data
    public static class Document {
        private String uploadDir = "./data/uploads";
        private String parsedDir = "./data/parsed";
        private String allowedTypes = "pdf,doc,docx,ppt,pptx,txt,md,html,xls,xlsx";
    }

    @Data
    public static class Chunk {
        /** 父块 Token 大小（父层递归字符分块 chunk_size） */
        private int parentTokenSize = 1000;
        /** 父块 Token 重叠（父层递归字符分块 chunk_overlap） */
        private int parentOverlap = 150;
        /** 子块 Token 大小（子层递归字符分块 chunk_size，约为父块的 1/3） */
        private int childTokenSize = 300;
        /** 子块 Token 重叠（子层递归字符分块 chunk_overlap） */
        private int childOverlap = 40;
    }

    @Data
    public static class Retrieval {
        /** 向量召回 Top K */
        private int vectorTopK = 20;
        /** 全文召回 Top K */
        private int fulltextTopK = 20;
        /** RRF 融合参数 k */
        private int rrfK = 60;
        /** Rerank 后保留 Top N */
        private int rerankTopN = 5;
    }

    @Data
    public static class Llm {
        /** LLM 上下文总 Token 预算 */
        private int contextTokenBudget = 8000;
        /** 预留给系统提示词、历史对话、用户问题的 Token */
        private int reserveToken = 2000;
        /** 强制引用标记前缀 */
        private String citationPrefix = "doc_";
    }

    @Data
    public static class Cache {
        private String prefix = "zbrain:";
        /** 文档处理进度 TTL（秒）- 1 天 */
        private long docProgressTtl = 86400L;
        /** 多轮对话上下文 TTL（秒）- 2 小时 */
        private long chatContextTtl = 7200L;
        /** Embedding 缓存 TTL（秒）- 7 天 */
        private long embeddingTtl = 604800L;
    }

    @Data
    public static class QueryPreprocess {
        /** 是否启用 Query 改写（多轮对话指代消解 + 单轮关键词扩展） */
        private boolean enableQueryRewrite = true;
    }
}
