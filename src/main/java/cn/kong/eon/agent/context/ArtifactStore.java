package cn.kong.eon.agent.context;

import cn.kong.eon.config.EonProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Artifact 外置存储（大结果落盘，上下文中只保留回取指针）。
 *
 * <p>单条工具结果超过 {@code eon.agent.context.artifact-threshold-chars}
 * 时，原始内容落盘到 {@code {artifactDir}/{sessionId}/{uuid}.log}，
 * 上下文中只保留语义标注 + {@code artifact://} 引用，模型可通过
 * 回取工具按需读取完整内容。</p>
 *
 * @author eon-team
 */
@Slf4j
@Component
public class ArtifactStore {

    public static final String URI_SCHEME = "artifact://";

    private final Path baseDir;

    public ArtifactStore(EonProperties properties) {
        this.baseDir = Paths.get(properties.getAgent().getContext().getArtifactDir());
    }

    /**
     * 保存原始内容，返回 artifact:// 回取指针
     *
     * @param sessionId 会话 ID（分目录隔离）
     * @param content   原始内容
     * @return artifact:// 引用 URI
     */
    public String save(String sessionId, String content) {
        try {
            String fileName = UUID.randomUUID() + ".log";
            Path dir = baseDir.resolve(sessionId);
            Files.createDirectories(dir);
            Path file = dir.resolve(fileName);
            Files.writeString(file, content, StandardCharsets.UTF_8);
            String uri = URI_SCHEME + sessionId + "/" + fileName;
            log.debug("[ArtifactStore] 外置成功: {} ({} chars)", uri, content.length());
            return uri;
        } catch (IOException e) {
            log.error("[ArtifactStore] 外置失败，退回 inline 截断", e);
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 按 artifact:// URI 回取完整内容
     */
    public String read(String uri) {
        if (uri == null || !uri.startsWith(URI_SCHEME)) {
            throw new IllegalArgumentException("非法 artifact URI: " + uri);
        }
        String relative = uri.substring(URI_SCHEME.length());
        // 防路径穿越
        if (relative.contains("..")) {
            throw new IllegalArgumentException("artifact URI 含非法路径: " + uri);
        }
        Path file = baseDir.resolve(relative);
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("artifact 回取失败: " + uri, e);
        }
    }
}
