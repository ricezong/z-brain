package cn.kong.zbrain.dto.response;

import cn.kong.zbrain.enums.ChatIntent;

/**
 * 意图识别结果
 *
 * @param intent          识别到的意图
 * @param confidence      置信度 0-1
 * @param reason          判断理由（用于审计 / 调试）
 * @param fallbackIntent  置信度不足时的回退意图
 *
 * @author zbrain-team
 */
public record IntentResult(
        ChatIntent intent,
        double confidence,
        String reason,
        ChatIntent fallbackIntent
) {

    /** 置信度阈值，低于此值则使用回退意图 */
    private static final double CONFIDENCE_THRESHOLD = 0.7;

    public boolean isConfident() {
        return confidence >= CONFIDENCE_THRESHOLD;
    }

    /**
     * 获取最终生效的意图：置信度足够则用识别结果，否则降级到回退意图
     */
    public ChatIntent getEffectiveIntent() {
        return isConfident() ? intent : fallbackIntent;
    }
}
