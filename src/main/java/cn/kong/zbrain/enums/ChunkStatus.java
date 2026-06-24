package cn.kong.zbrain.enums;

/**
 * 分块状态枚举
 *
 * <p>控制检索范围：仅 status=active 的分块参与检索。</p>
 *
 * @author zbrain-team
 */
public enum ChunkStatus {

    /** 草稿（待向量化或审核中） */
    DRAFT("draft"),
    /** 激活（可检索） */
    ACTIVE("active");

    private final String code;

    ChunkStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ChunkStatus fromCode(String code) {
        for (ChunkStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的分块状态: " + code);
    }
}
