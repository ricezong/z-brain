package cn.kong.eon.rag.parse;

import cn.kong.eon.common.exception.BusinessException;
import cn.kong.eon.persistence.entity.SysApiConfig;
import cn.kong.eon.config.ConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.llamacloud_prod.api.client.LlamaCloudClient;
import com.llamacloud_prod.api.client.okhttp.LlamaCloudOkHttpClient;
import com.llamacloud_prod.api.models.files.FileCreateParams;
import com.llamacloud_prod.api.models.files.FileCreateResponse;
import com.llamacloud_prod.api.models.parsing.ParsingCreateParams;
import com.llamacloud_prod.api.models.parsing.ParsingCreateResponse;
import com.llamacloud_prod.api.models.parsing.ParsingGetResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 基于 LlamaIndex Cloud Parsing API 的文档解析器。
 *
 * <p>调用链路：上传文件 → 创建 Parsing Job → 轮询 Job 状态 → 提取 Markdown 结果 → 文本清洗。
 * 相比 Tika 的纯文本提取，LlamaIndex Cloud Parsing 能更好地处理表格、版面布局、图文混排等复杂文档场景。
 * 支持所有文件类型，由前端上传时选择解析方式。</p>
 *
 * <p>配置由数据库 {@code sys_api_config} 表管理（configType = llama_index），通过 {@link ConfigService} 读取。
 * 每次解析时动态构建 {@link LlamaCloudClient}，配置变更无需重启服务。</p>
 *
 * @author eon-team
 */
@Slf4j
@Component
public class LlamaIndexParser {

    /** 配置类型标识 */
    public static final String CONFIG_TYPE = "llama_index";

    /** 默认解析级别 */
    private static final String DEFAULT_TIER = "AGENTIC";

    /** 轮询 Job 状态间隔（毫秒） */
    private static final long POLL_INTERVAL_MS = 3000L;

    /** 单个 Job 最大等待时间（毫秒） */
    private static final long JOB_TIMEOUT_MS = 300_000L;

    private final ConfigService configService;
    private final DocumentParser documentParser;
    private final ObjectMapper objectMapper;

    public LlamaIndexParser(ConfigService configService, DocumentParser documentParser,
                            ObjectMapper objectMapper) {
        this.configService = configService;
        this.documentParser = documentParser;
        this.objectMapper = objectMapper;
        log.info("LlamaIndexParser 已初始化（配置由数据库管理）");
    }

    /**
     * 是否已启用 LlamaIndex 解析
     *
     * @return true 表示已启用
     */
    public boolean isEnabled() {
        SysApiConfig config = configService.getApiConfig(CONFIG_TYPE);
        return config != null && Boolean.TRUE.equals(config.getEnabled());
    }

    /**
     * 根据当前数据库配置构建 LlamaCloudClient
     */
    private LlamaCloudClient buildClient(SysApiConfig config) {
        LlamaCloudOkHttpClient.Builder builder = LlamaCloudOkHttpClient.builder()
                .apiKey(config.getApiKey());
        if (config.getBaseUrl() != null && !config.getBaseUrl().isEmpty()) {
            builder.baseUrl(config.getBaseUrl());
        }
        return builder.build();
    }

    /**
     * 从 config JSON 字段中读取解析级别（tier），默认 AGENTIC
     */
    private String getTier(SysApiConfig config) {
        if (config.getConfig() == null || config.getConfig().isEmpty()) {
            return DEFAULT_TIER;
        }
        try {
            JsonNode node = objectMapper.readTree(config.getConfig());
            JsonNode tierNode = node.get("tier");
            return tierNode != null ? tierNode.asText() : DEFAULT_TIER;
        } catch (Exception e) {
            log.warn("解析 config JSON 失败，使用默认 tier={}: {}", DEFAULT_TIER, e.getMessage());
            return DEFAULT_TIER;
        }
    }

    /**
     * 通过 LlamaIndex Cloud Parsing API 解析文档。
     *
     * @param fileBytes 文件字节内容
     * @param filename  原始文件名（用于日志）
     * @return 清洗后的 Markdown 纯文本
     */
    public String parse(byte[] fileBytes, String filename) {
        long startTime = System.currentTimeMillis();
        log.info("LlamaIndex 开始解析文档: filename={}, size={}bytes", filename, fileBytes.length);

        SysApiConfig config = configService.getApiConfig(CONFIG_TYPE);
        LlamaCloudClient client = buildClient(config);

        // 1. 上传文件到 LlamaCloud
        String fileId = uploadFile(client, fileBytes, filename);

        // 2. 创建 Parsing Job
        String tier = getTier(config);
        String jobId = createParsingJob(client, tier, fileId);

        // 3. 轮询 Job 状态，获取 Markdown 结果
        String markdown = pollAndExtractResult(client, jobId);

        // 4. 复用 Tika 解析器的文本清洗逻辑（去页眉页脚、合并空行等）
        String cleaned = documentParser.cleanText(markdown);

        long cost = System.currentTimeMillis() - startTime;
        log.info("LlamaIndex 解析完成: filename={}, jobId={}, rawLen={}, cleanedLen={}, cost={}ms",
                filename, jobId, markdown.length(), cleaned.length(), cost);

        return cleaned;
    }

    /**
     * 上传文件到 LlamaCloud Files 服务
     */
    private String uploadFile(LlamaCloudClient client, byte[] fileBytes, String filename) {
        FileCreateParams params = FileCreateParams.builder()
                .purpose("parsing")
                .file(fileBytes)
                .build();

        FileCreateResponse response = client.files().create(params);
        String fileId = response.id();
        log.info("LlamaIndex 文件上传完成: filename={}, fileId={}", filename, fileId);
        return fileId;
    }

    /**
     * 创建 Parsing Job
     */
    private String createParsingJob(LlamaCloudClient client, String tierStr, String fileId) {
        // 目前 LlamaCloud API 仅支持 AGENTIC 级别
        ParsingCreateParams.Tier tierParam = ParsingCreateParams.Tier.AGENTIC;

        ParsingCreateParams params = ParsingCreateParams.builder()
                .tier(tierParam)
                .version(ParsingCreateParams.Version.LATEST)
                .fileId(fileId)
                .build();

        ParsingCreateResponse response = client.parsing().create(params);
        String jobId = response.id();
        log.info("LlamaIndex parsing job 已创建: fileId={}, jobId={}, tier={}", fileId, jobId, tierParam);
        return jobId;
    }

    /**
     * 轮询 Job 状态直到完成，提取 Markdown 文本
     *
     * @throws BusinessException Job 失败/取消/超时
     */
    private String pollAndExtractResult(LlamaCloudClient client, String jobId) {
        long deadline = System.currentTimeMillis() + JOB_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            ParsingGetResponse response = client.parsing().get(jobId);
            ParsingGetResponse.Job job = response.job();
            ParsingGetResponse.Job.Status status = job.status();

            log.debug("LlamaIndex parsing job 状态: jobId={}, status={}", jobId, status);

            if (status == ParsingGetResponse.Job.Status.COMPLETED) {
                // markdownFull() 返回完整的 Markdown 文本
                String markdown = response.markdownFull().orElse("");
                if (markdown.isEmpty()) {
                    // 回退到 textFull
                    markdown = response.textFull().orElse("");
                }
                if (markdown.isEmpty()) {
                    throw new BusinessException("LlamaIndex 解析完成但结果为空: jobId=" + jobId);
                }
                return markdown;
            }

            if (status == ParsingGetResponse.Job.Status.FAILED
                    || status == ParsingGetResponse.Job.Status.CANCELLED) {
                String errorMsg = job.errorMessage().orElse("unknown error");
                throw new BusinessException(
                        "LlamaIndex 解析失败: jobId=" + jobId + ", status=" + status + ", error=" + errorMsg);
            }

            // PENDING / RUNNING → 继续等待
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException("LlamaIndex 解析被中断: jobId=" + jobId, e);
            }
        }

        throw new BusinessException("LlamaIndex 解析超时: jobId=" + jobId
                + ", timeout=" + JOB_TIMEOUT_MS + "ms");
    }
}
