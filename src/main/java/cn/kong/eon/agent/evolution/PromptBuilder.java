package cn.kong.eon.agent.evolution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 系统提示词 md 资产装配器。
 *
 * <p>把 workspace 下的 md 资产（SOUL / USER / MEMORY）按容量预算拼为注入文本，
 * 供 {@code WorkspaceInjectAdvisor} 追加到系统提示词。</p>
 *
 * <p>容量预算（方案 4.6）：单文件 20KB（{@link WorkspaceService#ASSET_MAX_CHARS}），
 * 总量 150KB。MEMORY.md 为必载资产（≥200 字符）。</p>
 *
 * @author eon-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptBuilder {

    /** 注入总量预算（字符，约 150KB） */
    private static final int TOTAL_BUDGET = 150_000;

    private final WorkspaceService workspaceService;

    /**
     * 构建注入文本（SOUL + USER + MEMORY）
     *
     * @return 拼接后的注入文本；无任何资产时返回 null
     */
    public String buildInjection() {
        StringBuilder sb = new StringBuilder();
        int used = 0;
        used = appendAsset(sb, "SOUL.md", "人格（SOUL.md）", used);
        used = appendAsset(sb, "USER.md", "用户画像（USER.md）", used);
        // MEMORY 必载（readMemory 已含占位提示，跳过空记区）
        String memory = workspaceService.readMemory();
        if (memory != null && !memory.startsWith("(")) {
            used = appendRaw(sb, "长期记忆（MEMORY.md）", memory, used);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private int appendAsset(StringBuilder sb, String name, String label, int used) {
        String content = workspaceService.readAsset(name);
        return appendRaw(sb, label, content, used);
    }

    private int appendRaw(StringBuilder sb, String label, String content, int used) {
        if (content == null || content.isBlank()) {
            return used;
        }
        if (used + content.length() > TOTAL_BUDGET) {
            int remain = TOTAL_BUDGET - used;
            if (remain <= 0) {
                return used;
            }
            content = content.substring(0, remain) + "\n...[已超总量预算]";
        }
        sb.append("\n\n# ").append(label).append("\n").append(content);
        return used + content.length();
    }
}
