package cn.kong.eon.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * InnerTool → Spring AI {@link ToolCallback} 适配器
 *
 * <p>职责：</p>
 * <ul>
 *   <li>把 InnerTool 的业务 schema 注入必填 {@code reason} 参数后，
 *       构建 {@link ToolDefinition} 暴露给模型（1.1.8 标准：
 *       ToolDefinition.builder().name().description().inputSchema()）</li>
 *   <li>执行委托给 InnerTool，结果经 {@link ToolResultPostProcessor}
 *       语义标注后回灌；异常包装为 FAILED 标注，不让堆栈直接进入上下文</li>
 *   <li>★ 补发 TOOL_CALL SSE 事件（设计文档 §4.3：工具调用可视化是 Agent 体验关键）</li>
 * </ul>
 *
 * @author eon-team
 */
@Slf4j
public class InnerToolCallback implements ToolCallback {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final InnerTool tool;
    private final ToolResultPostProcessor postProcessor;
    private final ToolDefinition toolDefinition;

    public InnerToolCallback(InnerTool tool, ToolResultPostProcessor postProcessor) {
        this.tool = tool;
        this.postProcessor = postProcessor;
        this.toolDefinition = ToolDefinition.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(injectReasonParam(tool.jsonSchema()))
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        // ★ 补发 TOOL_CALL SSE 事件（设计文档 §4.3）
        String reason = extractReason(toolInput);
        ToolExecutionContext.pushToolCall(tool.name(), reason, toolInput);

        try {
            String raw = tool.execute(toolInput);
            return postProcessor.wrap(tool.name(), toolInput, raw);
        } catch (Exception e) {
            log.warn("[InnerToolCallback] 工具执行失败: {}", tool.name(), e);
            return postProcessor.wrapError(tool.name(), toolInput, e);
        }
    }

    /**
     * 从工具参数 JSON 中提取 reason 字段（用于 SSE TOOL_CALL 事件展示）
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

    /**
     * 向工具 schema 注入必填 reason 参数
     *
     * <p>模型发起 toolCall 时必须说明调用动机，runtime 连同结果回传，
     * 提升长链条可解释性（近乎零成本）。schema 非法时原样返回并告警，
     * 不阻断工具注册。</p>
     */
    static String injectReasonParam(String originalSchema) {
        try {
            ObjectNode root = (ObjectNode) MAPPER.readTree(originalSchema);
            ObjectNode properties = root.with("properties");
            ObjectNode reason = properties.putObject("reason");
            reason.put("type", "string");
            reason.put("description", "为什么需要调用本工具，一句话说明动机");

            ArrayNode required = root.withArray("required");
            boolean hasReason = false;
            for (JsonNode n : required) {
                if ("reason".equals(n.asText())) {
                    hasReason = true;
                    break;
                }
            }
            if (!hasReason) {
                required.add("reason");
            }
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("[InnerToolCallback] schema 注入 reason 失败，使用原始 schema: {}", e.getMessage());
            return originalSchema;
        }
    }
}
