package cn.kong.eon.agent.tool;

import cn.kong.eon.agent.context.ArtifactStore;
import cn.kong.eon.config.EonProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工具结果后处理器（将 tool result 包装为语义标注 + 超长结果外置 artifact）
 *
 * <p>将原始工具输出整理为带固定字段的语义标注格式：</p>
 * <ul>
 *   <li><b>action</b>：执行了什么（工具名+关键参数）</li>
 *   <li><b>reason</b>：为什么执行（取自工具调用的 reason 参数）</li>
 *   <li><b>status</b>：SUCCESS / FAILED</li>
 *   <li><b>fullContent</b>：inline 或 artifact:// 路径</li>
 * </ul>
 *
 * <p>超长结果自动外置：超过阈值的工具输出通过 {@link ArtifactStore} 落盘，
 * 上下文中仅保留摘要 + artifact 引用（dataBus / artifact 分离原则）。</p>
 *
 * @author eon-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolResultPostProcessor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 外置后保留在上下文中的最大字符数 */
    private static final int TRUNCATED_KEEP_CHARS = 2000;

    private final ArtifactStore artifactStore;
    private final EonProperties properties;

    /**
     * 包装成功结果为语义标注格式
     *
     * @param toolName  工具名
     * @param argsJson  调用参数 JSON（含 reason）
     * @param rawResult 原始工具输出
     * @return 包装后的 Markdown 格式结果
     */
    public String wrap(String toolName, String argsJson, String rawResult) {
        return doWrap(toolName, extractReason(argsJson), "SUCCESS", rawResult);
    }

    /**
     * 包装失败结果为语义标注格式
     */
    public String wrapError(String toolName, String argsJson, Exception e) {
        String detail = "ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        return doWrap(toolName, extractReason(argsJson), "FAILED", detail);
    }

    private String doWrap(String toolName, String reason, String status, String rawResult) {
        String body = rawResult == null ? "(empty)" : rawResult;
        String fullContent = "inline";

        int threshold = properties.getAgent().getContext().getArtifactThresholdChars();
        if (body.length() > threshold) {
            try {
                fullContent = artifactStore.save(ToolExecutionContext.sessionId(), body);
                body = body.substring(0, Math.min(TRUNCATED_KEEP_CHARS, body.length()))
                        + "\n... [结果已截断，完整内容请通过 fullContent 引用获取]";
            } catch (Exception ex) {
                // artifact 落盘失败时降级为截断保留
                log.warn("[ToolResultPostProcessor] artifact 落盘失败，降级截断", ex);
                body = body.substring(0, Math.min(TRUNCATED_KEEP_CHARS, body.length()))
                        + "\n... [结果已截断，artifact 落盘失败]";
                fullContent = "inline (artifact save failed)";
            }
        }

        return """
                ### Observation
                - action: %s
                - reason: %s
                - status: %s
                - fullContent: %s

                %s
                """.formatted(toolName, reason, status, fullContent, body);
    }

    /**
     * 从调用参数 JSON 中提取 reason 字段（模型调用工具时必填的动机说明）
     */
    private String extractReason(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) {
            return "(未填写)";
        }
        try {
            JsonNode node = MAPPER.readTree(argsJson);
            JsonNode reason = node.get("reason");
            return reason != null ? reason.asText() : "(未填写)";
        } catch (Exception e) {
            return "(解析失败)";
        }
    }
}
