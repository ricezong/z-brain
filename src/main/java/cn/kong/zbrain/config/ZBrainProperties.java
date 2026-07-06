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
     * LlamaIndex Cloud 文档解析配置（PDF 专用）
     */
    private LlamaIndex llamaIndex = new LlamaIndex();

    @Data
    public static class Document {
        private String uploadDir = "./data/uploads";
        private String parsedDir = "./data/parsed";
        private String allowedTypes = "pdf,doc,docx,ppt,pptx,txt,md,html,xls,xlsx";
    }

    @Data
    public static class Chunk {
        /** 父块最小 Token 数（语义边界控制下限） */
        private int parentMinTokenSize = 512;
        /** 父块最大 Token 数（语义边界控制上限） */
        private int parentMaxTokenSize = 2048;
        /** 子块 Token 大小（递归字符分块 chunk_size） */
        private int childTokenSize = 256;
        /** 子块 Token 重叠（约为 chunk_size 的 12.5%） */
        private int tokenOverlap = 32;
    }

    @Data
    public static class Retrieval {
        /** 向量召回 Top K */
        private int vectorTopK = 20;
        /** 全文召回 Top K */
        private int fulltextTopK = 20;
        /** 模糊召回 Top K */
        private int fuzzyTopK = 10;
        /** RRF 融合参数 k */
        private int rrfK = 60;
        /** Rerank 后保留 Top N */
        private int rerankTopN = 5;
        /** 模糊匹配相似度阈值 */
        private double fuzzyThreshold = 0.3;
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
    public static class LlamaIndex {
        /** 是否启用 LlamaIndex Cloud 解析（仅对 PDF 生效） */
        private boolean enabled = true;
        /** LlamaCloud API Key（也可通过环境变量 LLAMA_CLOUD_API_KEY 注入） */
        private String apiKey = "llx-mEpMFEoBrnhwZow0q1blHBeCXheiEGBcFHxdL2qkHSuUXQAk";
        /** LlamaCloud API Base URL */
        private String baseUrl = "https://api.cloud.llamaindex.ai";
        /** Parsing tier: AGENTIC / STANDARD */
        private String tier = "AGENTIC";
        /** 轮询 job 状态间隔（毫秒） */
        private long pollIntervalMs = 3000L;
        /** 单个 job 最大等待时间（毫秒），默认 5 分钟 */
        private long jobTimeoutMs = 300000L;
    }
}
