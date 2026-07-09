package cn.kong.zbrain.util;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;


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

}
