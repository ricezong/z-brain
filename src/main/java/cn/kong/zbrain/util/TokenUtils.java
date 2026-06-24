package cn.kong.zbrain.util;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

import java.util.Collections;
import java.util.List;

/**
 * Token 计数工具
 *
 * <p>基于 jtokkit 库实现，使用 cl100k_base 编码（与 GPT/Qwen 系列模型 Token 计数接近）。</p>
 *
 * @author zbrain-team
 */
public class TokenUtils {

    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();
    private static final Encoding ENCODING = REGISTRY.getEncoding(EncodingType.CL100K_BASE);

    private TokenUtils() {
    }

    /**
     * 计算文本的 Token 数
     */
    public static int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return ENCODING.countTokens(text);
    }

    /**
     * 批量计算 Token 数
     */
    public static List<Integer> countTokensBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        return texts.stream().map(TokenUtils::countTokens).toList();
    }

    /**
     * 按目标 Token 大小切分文本
     *
     * @param text       原始文本
     * @param targetSize 目标 Token 大小
     * @param overlap    重叠 Token 数
     * @return 切分后的文本列表
     */
    public static List<String> splitByTokenSize(String text, int targetSize, int overlap) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        // 简化实现：按字符近似切分（1 Token ≈ 1.5 个中文字符 / 4 个英文字符）
        // 生产环境建议使用编码后的 Token 列表精确切分
        int avgCharsPerToken = 2;
        int charSize = targetSize * avgCharsPerToken;
        int overlapChars = overlap * avgCharsPerToken;

        List<String> chunks = new java.util.ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + charSize, text.length());
            chunks.add(text.substring(start, end));
            if (end >= text.length()) {
                break;
            }
            start = end - overlapChars;
            if (start < 0) {
                start = 0;
            }
        }
        return chunks;
    }
}
