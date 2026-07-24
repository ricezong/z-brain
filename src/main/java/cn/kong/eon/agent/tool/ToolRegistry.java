package cn.kong.eon.agent.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心
 *
 * <p>启动时自动扫描所有 {@link InnerTool} Bean，适配为
 * {@link ToolCallback} 并缓存（按 name 索引，Bean 单例复用）。</p>
 *
 * <p>未来扩展：通过 Sprint 扫描 SkillScanner 加载 SKILL.md 动态技能，
 * 将技能注册为临时工具。当前仅支持静态注册的内置工具。</p>
 *
 * @author eon-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistry {

    private final List<InnerTool> innerTools;
    private final ToolResultPostProcessor postProcessor;

    /** name -> callback 缓存（单例复用，避免重复创建） */
    private final Map<String, ToolCallback> callbackCache = new ConcurrentHashMap<>();

    /**
     * 获取全部工具的 ToolCallback 数组（供 ChatClient.toolCallbacks(...) 使用）
     */
    public ToolCallback[] all() {
        return innerTools.stream()
                .map(this::callbackOf)
                .toArray(ToolCallback[]::new);
    }

    /**
     * 按名称获取单个工具的 ToolCallback（供按需调用）
     */
    public ToolCallback byName(String name) {
        return innerTools.stream()
                .filter(t -> t.name().equals(name))
                .findFirst()
                .map(this::callbackOf)
                .orElse(null);
    }

    /**
     * 获取全部已注册工具的名称列表
     */
    public List<String> toolNames() {
        return innerTools.stream().map(InnerTool::name).toList();
    }

    private ToolCallback callbackOf(InnerTool tool) {
        return callbackCache.computeIfAbsent(tool.name(),
                k -> new InnerToolCallback(tool, postProcessor));
    }
}
