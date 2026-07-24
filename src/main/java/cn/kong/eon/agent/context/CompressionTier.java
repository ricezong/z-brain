package cn.kong.eon.agent.context;

/**
 * 压缩层级枚举（四级水位线）
 *
 * <p>ordinal 保证比较顺序：{@link #SNIP}(0) &lt; {@link #PRUNE}(1) &lt; {@link #COMPACT}(2)，
 * 确保压缩策略单调度推进，保 Prompt Cache 前缀稳定（方案铁律 #1），
 * 避免"先 COMPACT 再 SNIP"导致的 cache prefix 全量失效。</p>
 *
 * @author eon-team
 */
public enum CompressionTier {

    /** 60%～70%，轻量裁剪：截断尾部冗余工具结果，不改 LLM 上下文结构 */
    SNIP,
    /** 80%～85%，占位替换：将旧消息替换为摘要占位 + 保留 assistant 消息，不改 LLM 上下文结构 */
    PRUNE,
    /** ≥95%，LLM 增量摘要：合并上次摘要与新增对话 */
    COMPACT;

    /**
     * 判断当前层级是否达到或超过指定层级
     */
    public boolean isAtLeast(CompressionTier other) {
        return this.ordinal() >= other.ordinal();
    }
}
