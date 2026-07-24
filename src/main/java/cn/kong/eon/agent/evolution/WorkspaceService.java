package cn.kong.eon.agent.evolution;

import cn.kong.eon.config.EonProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

/**
 * Workspace md 资产服务（简化版）。
 *
 * <p>S1-2 阶段先落地 MEMORY.md 的读写，供 save_memory / memory_search 工具使用。
 * 完整的 md 体系（SOUL/USER/AGENTS/TOOLS + PromptBuilder 容量预算）在 S3-4 落地。</p>
 *
 * <p>MEMORY.md 容量上限 {@value #MAX_MEMORY_CHARS} 字符（方案 4.6：必载资产），
 * 超限写回失败并返回全量，倒逼模型整理（精简/合并/删除过期项）再重试。</p>
 *
 * @author eon-team
 */
@Slf4j
@Component
public class WorkspaceService {

    /** MEMORY.md 容量上限（字符），与方案 4.6 一致 */
    public static final int MAX_MEMORY_CHARS = 2200;

    /** 单个 md 资产容量上限（字符，约 20KB），与方案 4.6 一致 */
    public static final int ASSET_MAX_CHARS = 20480;

    private final Path memoryFile;

    public WorkspaceService(EonProperties properties) {
        this.memoryFile = Paths.get(properties.getAgent().getWorkspaceDir()).resolve("MEMORY.md");
    }

    /**
     * 读取 MEMORY.md 全量
     *
     * @return 全文；文件不存在时返回占位提示
     */
    public String readMemory() {
        if (!Files.exists(memoryFile)) {
            return "(暂无长期记忆)";
        }
        try {
            String content = Files.readString(memoryFile, StandardCharsets.UTF_8);
            return content.isBlank() ? "(长期记忆为空)" : content;
        } catch (IOException e) {
            log.error("[WorkspaceService] 读取 MEMORY.md 失败: {}", memoryFile, e);
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 读取 workspace 下指定 md 资产（SOUL.md / USER.md / AGENTS.md 等）
     *
     * @param name 文件名（相对 workspace 目录）
     * @return 全文；不存在返回 null；超 {@link #ASSET_MAX_CHARS} 截断
     */
    public String readAsset(String name) {
        if (name == null || name.contains("..")) {
            return null; // 防路径穿越
        }
        Path parent = memoryFile.getParent();
        if (parent == null) {
            return null;
        }
        Path file = parent.resolve(name);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.length() > ASSET_MAX_CHARS) {
                content = content.substring(0, ASSET_MAX_CHARS)
                        + "\n...[资产超过 " + ASSET_MAX_CHARS + " 字符，已截断]";
            }
            return content;
        } catch (IOException e) {
            log.warn("[WorkspaceService] 读取资产失败: {} err={}", name, e.getMessage());
            return null;
        }
    }

    /**
     * 追加一条记忆到 MEMORY.md
     *
     * <p>格式：{@code - [yyyy-MM-dd] 内容}。超过 {@link #MAX_MEMORY_CHARS} 上限时
     * 不写回，返回全量内容提示模型先整理（精简/合并/删除过期项）再重试。</p>
     *
     * @param content 要记住的内容（可独立理解的陈述）
     * @return 操作结果描述（含当前 MEMORY.md 全文，供模型确认）
     */
    public String appendMemory(String content) {
        if (content == null || content.isBlank()) {
            return "content 为空，未写入。";
        }
        try {
            Path parent = memoryFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String existing = Files.exists(memoryFile)
                    ? Files.readString(memoryFile, StandardCharsets.UTF_8) : "";
            String entry = "- [" + LocalDate.now() + "] " + content.strip().replaceAll("\\s+", " ") + "\n";
            String merged = existing + entry;

            if (merged.length() > MAX_MEMORY_CHARS) {
                return "MEMORY.md 达到 " + MAX_MEMORY_CHARS + " 字符上限，本次未写入。"
                        + "请先整理现有记忆（精简/合并/删除过期项）后再重试。当前全量内容：\n" + existing;
            }
            Files.writeString(memoryFile, merged, StandardCharsets.UTF_8);
            log.info("[WorkspaceService] 记忆已写入 MEMORY.md ({} chars)", merged.length());
            return "已写入长期记忆。当前 MEMORY.md 内容：\n" + merged;
        } catch (IOException e) {
            log.error("[WorkspaceService] 写入 MEMORY.md 失败: {}", memoryFile, e);
            throw new UncheckedIOException("长期记忆写入失败", e);
        }
    }
}
