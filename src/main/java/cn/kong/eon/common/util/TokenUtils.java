package cn.kong.eon.common.util;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;


/**
 * Token 估算工具类
 *
 * <p>基于 jtokkit 编码器，使用 cl100k_base 编码，兼容 GPT/Qwen 等模型的 Token 估算需求。</p>
 *
 * @author eon-team
 */
public class TokenUtils {

    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();
    private static final Encoding ENCODING = REGISTRY.getEncoding(EncodingType.CL100K_BASE);

    private TokenUtils() {
    }

    /**
     * 估算文本的 Token 数
     */
    public static int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return ENCODING.countTokens(text);
    }

}
