package cn.kong.eon.agent.tool.builtin;

import cn.kong.eon.agent.evolution.WorkspaceService;
import cn.kong.eon.agent.tool.InnerTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 长期记忆检索工具。
 *
 * <p>读取 workspace/MEMORY.md 全量供模型回忆用户偏好、过往决策、项目约定。
 * S1 阶段不依赖 PG 全文索引（ghparser），直接返回全量（容量受 2200 字符上限
 * 约束，可接）；S3-4 接入 PG 全文检索后改为按 query 召回。</p>
 *
 * @author eon-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemorySearchTool implements InnerTool {

    private final WorkspaceService workspaceService;

    @Override
    public String name() {
        return "memory_search";
    }

    @Override
    public String description() {
        return "检索长期记忆（MEMORY.md）。当需要回忆用户偏好、过往决策、项目约定、暗定规则时调用。"
                + "当前阶段返回全量记忆，请自行从中提取与问题相关的信息。";
    }

    @Override
    public String jsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "query": {
                      "type": "string",
                      "description": "想回忆的关键词或问题，用于定位相关记忆（当前返回全量，后续按此召回）"
                    }
                  },
                  "required": ["query"]
                }
                """;
    }

    @Override
    public String execute(String argumentsJson) {
        // S1 阶段：query 仅作记录，返回全量记忆
        return workspaceService.readMemory();
    }
}
