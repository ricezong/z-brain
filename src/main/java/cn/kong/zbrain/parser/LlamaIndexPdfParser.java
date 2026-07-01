package cn.kong.zbrain.parser;

import cn.kong.zbrain.common.BusinessException;
import cn.kong.zbrain.config.ZBrainProperties;
import com.llamacloud_prod.api.client.LlamaCloudClient;
import com.llamacloud_prod.api.client.okhttp.LlamaCloudOkHttpClient;
import com.llamacloud_prod.api.core.http.ProxyAuthenticator;
import com.llamacloud_prod.api.models.files.FileCreateParams;
import com.llamacloud_prod.api.models.files.FileCreateResponse;
import com.llamacloud_prod.api.models.parsing.ParsingCreateParams;
import com.llamacloud_prod.api.models.parsing.ParsingCreateResponse;
import com.llamacloud_prod.api.models.parsing.ParsingGetResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * 基于 LlamaIndex Cloud Parsing API 的 PDF 解析器
 *
 * <p>调用链路：上传文件 → 创建 Parsing Job → 轮询 Job 状态 → 提取 Markdown 结果 → 文本清洗。
 * 相比 Tika 的纯文本提取，LlamaIndex Cloud Parsing 能更好地处理表格、版面布局、图文混排等复杂 PDF 场景。</p>
 *
 * <p>仅在 {@code zbrain.llama-index.enabled=true} 时装配；
 * 未启用时 PDF 解析自动回退到 {@link DocumentParser}（Tika）。</p>
 *
 * @author zbrain-team
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "zbrain.llama-index", name = "enabled", havingValue = "true")
public class LlamaIndexPdfParser {

    private final LlamaCloudClient client;
    private final ZBrainProperties properties;
    private final DocumentParser documentParser;

    public LlamaIndexPdfParser(ZBrainProperties properties, DocumentParser documentParser) {
        this.properties = properties;
        this.documentParser = documentParser;

        ZBrainProperties.LlamaIndex config = properties.getLlamaIndex();
        LlamaCloudOkHttpClient.Builder builder = LlamaCloudOkHttpClient.builder()
                .apiKey(config.getApiKey());
        if (config.getBaseUrl() != null && !config.getBaseUrl().isEmpty()) {
            builder.baseUrl(config.getBaseUrl());
        }

        this.client = builder.build();

        log.info("LlamaIndexPdfParser 已初始化: baseUrl={}", config.getBaseUrl());
    }

    /**
     * 通过 LlamaIndex Cloud Parsing API 解析 PDF 文件
     *
     * @param fileBytes PDF 文件字节内容
     * @param filename  原始文件名（用于日志）
     * @return 清洗后的 Markdown 纯文本
     */
    public String parse(byte[] fileBytes, String filename) {
        long startTime = System.currentTimeMillis();
        log.info("LlamaIndex 开始解析 PDF: filename={}, size={}bytes", filename, fileBytes.length);

        // 1. 上传文件到 LlamaCloud
        String fileId = uploadFile(fileBytes, filename);

        // 2. 创建 Parsing Job
        String jobId = createParsingJob(fileId);

        // 3. 轮询 Job 状态，获取 Markdown 结果
        String markdown = pollAndExtractResult(jobId);

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
    private String uploadFile(byte[] fileBytes, String filename) {
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
    private String createParsingJob(String fileId) {
        ParsingCreateParams params = ParsingCreateParams.builder()
                .tier(ParsingCreateParams.Tier.AGENTIC)
                .version(ParsingCreateParams.Version.LATEST)
                .fileId(fileId)
                .build();

        ParsingCreateResponse response = client.parsing().create(params);
        String jobId = response.id();
        log.info("LlamaIndex parsing job 已创建: fileId={}, jobId={}", fileId, jobId);
        return jobId;
    }

    /**
     * 轮询 Job 状态直到完成，提取 Markdown 文本
     *
     * @throws BusinessException Job 失败/取消/超时
     */
    private String pollAndExtractResult(String jobId) {
        ZBrainProperties.LlamaIndex config = properties.getLlamaIndex();
        long deadline = System.currentTimeMillis() + config.getJobTimeoutMs();
        long interval = config.getPollIntervalMs();

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
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException("LlamaIndex 解析被中断: jobId=" + jobId, e);
            }
        }

        throw new BusinessException("LlamaIndex 解析超时: jobId=" + jobId
                + ", timeout=" + config.getJobTimeoutMs() + "ms");
    }
}
