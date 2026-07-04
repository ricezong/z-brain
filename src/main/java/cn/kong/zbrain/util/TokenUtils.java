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
     * <p>采用递归字符分块策略：按分隔符优先级（{@code \n\n → \n → 。→ ！→ ？→ ；→ . → 空格 → 强制切}）
     * 递归降级切分，尽量在自然语义边界断开，避免把句子/段落从中间斩断。token 计数基于 jtokkit
     * cl100k_base 精确计算，相邻块通过 overlap 滑动窗口保持上下文连续。</p>
     *
     * <p>实现委托给 {@link RecursiveCharacterSplitter}，具体切分逻辑见该类。</p>
     *
     * @param text       原始文本
     * @param targetSize 目标 Token 大小
     * @param overlap    重叠 Token 数
     * @return 切分后的文本列表
     */
    public static List<String> splitByTokenSize(String text, int targetSize, int overlap) {
        return RecursiveCharacterSplitter.split(text, targetSize, overlap);
    }
}
