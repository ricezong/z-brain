package cn.kong.zbrain.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 对话响应
 *
 * @author zbrain-team
 */
@Data
public class ChatResponse {
    /** 会话 ID */
    private String sessionId;
    /** 原始问题 */
    private String query;
    /** 改写后的问题 */
    private String rewrittenQuery;
    /** 最终回答 */
    private String answer;
    /** 引用列表 */
    private List<Citation> citations;
    /** 命中分块 ID */
    private List<Long> hitChunkIds;
    /** Token 消耗 */
    private TokenMeta tokenMeta;
    /** 耗时（毫秒） */
    private Long costTimeMs;
    /** 意图（rag / chitchat） */
    private String intent;
    /** 模型显示名称 */
    private String modelName;

    @Data
    public static class Citation {
        /** 引用编号，如 doc_1 */
        private String label;
        /** 分块 ID */
        private Long chunkId;
        /** 文档 ID */
        private Long docId;
        /** 文档名称 */
        private String docName;
        /** 引用片段内容（截断摘要） */
        private String snippet;
        /** 引用完整内容（父块内容，用于弹窗展示） */
        private String fullContent;
        /** 页码 */
        private Integer page;
    }

    @Data
    public static class TokenMeta {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }
}
