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
    /** HyDE 假设答案 */
    private String hydeAnswer;
    /** 最终回答 */
    private String answer;
    /** 引用列表 */
    private List<Citation> citations;
    /** 命中分块 ID */
    private List<Long> hitChunkIds;
    /** Token 消耗 */
    private TokenUsage tokenUsage;
    /** 耗时（毫秒） */
    private Long costTimeMs;

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
        /** 引用片段内容 */
        private String snippet;
        /** 页码 */
        private Integer page;
    }

    @Data
    public static class TokenUsage {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }
}
