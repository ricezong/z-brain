package cn.kong.zbrain.enums;

/**
 * 分块类型枚举
 *
 * @author zbrain-team
 */
public enum ChunkType {

    /** 父块：保留完整语义（1000 Token），不向量化 */
    PARENT("parent"),
    /** 子块：用于精确检索（200 Token），向量化 */
    CHILD("child");

    private final String code;

    ChunkType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
