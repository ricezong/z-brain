package cn.kong.eon.agent.tool.builtin;

import org.springframework.stereotype.Component;
import cn.kong.eon.agent.tool.InnerTool;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 当前时间工具（浮模型时间幻觉）。
 *
 * @author eon-team
 */
@Component
public class TimeTool implements InnerTool {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss EEEE");

    @Override
    public String name() {
        return "get_current_time";
    }

    @Override
    public String description() {
        return "获取当前真实日期时间。当问题涉及「今天」「最近」「本周/deadline」等相对时间，"
                + "或需要确认当前日期时使用。";
    }

    @Override
    public String jsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {},
                  "required": []
                }
                """;
    }

    @Override
    public String execute(String argumentsJson) {
        return "{\"current_time\": \"" + LocalDateTime.now().format(FMT) + "\"}";
    }
}
