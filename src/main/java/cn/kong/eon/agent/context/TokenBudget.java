package cn.kong.eon.agent.context;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Token 预算估算器
 *
 * <p>使用 JTokkit（cl100k_base）对消息列表/文本进行 token 估算，
 * 用于 Advisor before 阶段判断是否触发压缩。优先使用上一轮 LLM 返回的真实
 * usage.totalTokens（从 Redis 读取），仅在无记录时降级用 JTokkit 估算。
 *
 * <p>估算精度：每条消息额外计 4 token overhead（role/content 分隔符），
 * 与 OpenAI 官方计数器基本对齐。</p>
 *
 * @author eon-team
 */
@Slf4j
@Component
public class TokenBudget {

    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();
    /** cl100k_base（JTokkit 1.0.0 默认编码，兼容 DeepSeek/Qwen 等模型） */
    private static final Encoding ENCODING = REGISTRY.getEncoding(EncodingType.CL100K_BASE);

    /** 每条消息额外 overhead（role / 分隔符等，对齐 OpenAI 计数器） */
    private static final int MESSAGE_OVERHEAD = 4;

    /**
     * 估算消息列表总 token 数
     */
    public int estimateMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Message m : messages) {
            total += estimateMessage(m);
        }
        return total;
    }

    /**
     * 估算单条消息 token（含 overhead + 文本 token）
     */
    public int estimateMessage(Message m) {
        if (m == null) {
            return 0;
        }
        return MESSAGE_OVERHEAD + estimateText(m.getText());
    }

    /**
     * 估算文本 token 数
     */
    public int estimateText(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        try {
            return ENCODING.countTokens(text);
        } catch (Exception e) {
            // 估算失败时降级为字符数/3（粗略近似）
            log.debug("[TokenBudget] JTokkit 估算异常降级: {}", e.getMessage());
            return text.length() / 3;
        }
    }
}
