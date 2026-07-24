package cn.kong.eon.agent.tool.builtin;

import cn.kong.eon.agent.evolution.WorkspaceService;
import cn.kong.eon.agent.tool.InnerTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 长期记忆写入工具。
 *
 * <p>把值得跨会话保留的信息写入 workspace/MEMORY.md。触发场景：用户明确要求
 * "记住"、对话中沉淀出稳定的用户偏好/项目约定/重要决策/反复规律。不存在临时信息、
 * 单次任务细节、可经工具重新获取的数据。</p>
 *
 * @author eon-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaveMemoryTool implements InnerTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WorkspaceService workspaceService;

    @Override
    public String name() {
        return "save_memory";
    }

    @Override
    public String description() {
        return "将值得长期保留的信息写入长期记忆（MEMORY.md）。"
                + "当用户明确要求记住某事，或对话中产生稳定的用户偏好、项目约定、重要决策、"
                + "反复出现的规律时调用。不要存临时信息、单次任务细节、可从工具重新获取的数据。";
    }

    @Override
    public String jsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "content": {
                      "type": "string",
                      "description": "要记住的内容，写成可独立理解、跨会话可引用的陈述"
                    }
                  },
                  "required": ["content"]
                }
                """;
    }

    @Override
    public String execute(String argumentsJson) {
        try {
            var args = MAPPER.readTree(argumentsJson);
            String content = args.path("content").asText();
            if (content == null || content.isBlank()) {
                ObjectNode err = MAPPER.createObjectNode();
                err.put("saved", false);
                err.put("error", "content 不能为空");
                return MAPPER.writeValueAsString(err);
            }
            return workspaceService.appendMemory(content);
        } catch (Exception e) {
            log.warn("[SaveMemoryTool] 写入失败", e);
            throw new IllegalStateException("长期记忆写入失败: " + e.getMessage(), e);
        }
    }
}
