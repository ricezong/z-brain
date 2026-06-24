package cn.kong.zbrain.enums;

/**
 * 文档状态枚举
 *
 * <p>文档状态机流转：</p>
 * <pre>
 *   PENDING -> PARSING -> PENDING_REVIEW -> EMBEDDING -> SUCCESS
 *                                    |             |
 *                                    v             v
 *                              (审核驳回回到 PENDING_REVIEW)   FAILED
 * </pre>
 *
 * @author zbrain-team
 */
public enum DocumentStatus {

    /** 待解析 */
    PENDING("pending"),
    /** 解析中 */
    PARSING("parsing"),
    /** 待审核 */
    PENDING_REVIEW("pending_review"),
    /** 向量化中 */
    EMBEDDING("embedding"),
    /** 完成 */
    SUCCESS("success"),
    /** 失败 */
    FAILED("failed");

    private final String code;

    DocumentStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static DocumentStatus fromCode(String code) {
        for (DocumentStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的文档状态: " + code);
    }
}
