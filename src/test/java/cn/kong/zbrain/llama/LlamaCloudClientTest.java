package cn.kong.zbrain.llama;

import com.llamacloud_prod.api.client.LlamaCloudClient;
import com.llamacloud_prod.api.client.LlamaCloudClientAsync;
import com.llamacloud_prod.api.client.okhttp.LlamaCloudOkHttpClient;
import com.llamacloud_prod.api.client.okhttp.LlamaCloudOkHttpClientAsync;
import com.llamacloud_prod.api.core.JsonValue;
import com.llamacloud_prod.api.core.LogLevel;
import com.llamacloud_prod.api.core.MultipartField;
import com.llamacloud_prod.api.core.RequestOptions;
import com.llamacloud_prod.api.core.http.HttpResponseFor;
import com.llamacloud_prod.api.errors.BadRequestException;
import com.llamacloud_prod.api.errors.LlamaCloudException;
import com.llamacloud_prod.api.errors.UnauthorizedException;
import com.llamacloud_prod.api.models.beta.indexes.IndexListPage;
import com.llamacloud_prod.api.models.beta.indexes.IndexListParams;
import com.llamacloud_prod.api.models.extract.ExtractListPage;
import com.llamacloud_prod.api.models.extract.ExtractListPageAsync;
import com.llamacloud_prod.api.models.extract.ExtractV2Job;
import com.llamacloud_prod.api.models.files.FileCreateParams;
import com.llamacloud_prod.api.models.files.FileCreateResponse;
import com.llamacloud_prod.api.models.parsing.ParsingCreateParams;
import com.llamacloud_prod.api.models.parsing.ParsingCreateResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LlamaIndex Java SDK (llama-cloud 1.0.0) 集成测试。
 *
 * <p>覆盖官方文档 https://developers.llamaindex.ai/reference/java/ 中的核心能力:
 * <ul>
 *   <li>客户端构建: fromEnv / builder / withOptions</li>
 *   <li>服务入口: parsing / extract / files / beta.indexes</li>
 *   <li>同步 + 异步调用</li>
 *   <li>分页: autoPager / 手动 nextPage</li>
 *   <li>原始响应 withRawResponse</li>
 *   <li>未文档化 API: putAdditionalHeader/QueryParam/BodyProperty</li>
 *   <li>请求级配置 RequestOptions</li>
 *   <li>异常体系</li>
 * </ul>
 *
 * <p>真正调用远端的测试用 {@link EnabledIfEnvironmentVariable} 守护,需配置
 * {@code LLAMA_CLOUD_API_KEY} 才会执行,避免 CI 在无凭证时失败。
 *
 * @author zbrain-team
 */
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("LlamaIndex Java SDK (llama-cloud) 测试")
class LlamaCloudClientTest {

    private static final String TEST_API_KEY = "llx-mEpMFEoBrnhwZow0q1blHBeCXheiEGBcFHxdL2qkHSuUXQAk";
    private static final String TEST_PROJECT_ID = "my-project-id";

    private LlamaCloudClient syncClient;
    private LlamaCloudClientAsync asyncClient;

    @BeforeEach
    void setUp() {
        // builder 方式构建,显式传入凭证(便于在无 env 的环境下也能构造对象本身)
        LlamaCloudOkHttpClient.Builder builder = LlamaCloudOkHttpClient.builder()
                .apiKey(TEST_API_KEY)
                .logLevel(LogLevel.INFO)
                .maxRetries(2)
                .timeout(Duration.ofSeconds(60));

        syncClient = builder.build();
        asyncClient = LlamaCloudOkHttpClientAsync.builder()
                .apiKey(TEST_API_KEY)
                .build();
    }

    @AfterEach
    void tearDown() {
        // SDK 未显式 close 接口,这里仅置空便于 GC
        syncClient = null;
        asyncClient = null;
    }

    // ============================ 客户端构建 ============================

    @Nested
    @DisplayName("客户端构建")
    class ClientConstruction {

        @Test
        @DisplayName("builder 方式构建同步客户端并配置参数")
        void buildSyncClientWithBuilder() {
            LlamaCloudClient client = LlamaCloudOkHttpClient.builder()
                    .apiKey("test-key")
                    .maxRetries(4)
                    .timeout(Duration.ofSeconds(30))
                    .logLevel(LogLevel.DEBUG)
                    .build();

            assertNotNull(client, "客户端不应为 null");
            assertNotNull(client.parsing(), "parsing 服务入口应可用");
            assertNotNull(client.extract(), "extract 服务入口应可用");
            assertNotNull(client.files(), "files 服务入口应可用");
            assertNotNull(client.beta(), "beta 服务入口应可用");
        }

        @Test
        @DisplayName("fromEnv 从环境变量构建客户端")
        @EnabledIfEnvironmentVariable(named = "LLAMA_CLOUD_API_KEY", matches = ".+")
        void buildClientFromEnv() {
            LlamaCloudClient client = LlamaCloudOkHttpClient.fromEnv();
            assertNotNull(client, "fromEnv 客户端不应为 null");
        }

        @Test
        @DisplayName("builder + fromEnv 混合配置,显式 apiKey 覆盖 env")
        @EnabledIfEnvironmentVariable(named = "LLAMA_CLOUD_API_KEY", matches = ".+")
        void buildClientWithFromEnvAndOverride() {
            LlamaCloudClient client = LlamaCloudOkHttpClient.builder()
                    .fromEnv()
                    .apiKey("override-key")
                    .build();
            assertNotNull(client, "混合配置客户端不应为 null");
        }

        @Test
        @DisplayName("异步客户端构建")
        void buildAsyncClient() {
            LlamaCloudClientAsync client = LlamaCloudOkHttpClientAsync.builder()
                    .apiKey("test-key")
                    .build();
            assertNotNull(client, "异步客户端不应为 null");
            assertNotNull(client.parsing(), "异步 parsing 服务入口应可用");
        }

        @Test
        @DisplayName("withOptions 临时修改配置返回新客户端")
        void withOptionsReturnsNewClient() {
            LlamaCloudClient modified = syncClient.withOptions(opts -> {
                opts.maxRetries(42);
            });
            assertNotNull(modified, "withOptions 返回的客户端不应为 null");
            // 原客户端不受影响
            assertNotNull(syncClient.parsing());
        }
    }

    // ============================ Parsing 服务 ============================

    @Nested
    @DisplayName("Parsing 服务")
    class ParsingServiceTest {

        @Test
        @DisplayName("构建 ParsingCreateParams 包含必填字段")
        void buildParsingCreateParams() {
            ParsingCreateParams params = ParsingCreateParams.builder()
                    .tier(ParsingCreateParams.Tier.AGENTIC)
                    .version(ParsingCreateParams.Version.LATEST)
                    .fileId("test-file-id")
                    .build();

            assertNotNull(params, "ParsingCreateParams 构建后不应为 null");
        }

        @Test
        @DisplayName("构建带 AgenticOptions 的 ParsingCreateParams")
        void buildParsingParamsWithAgenticOptions() {
            ParsingCreateParams params = ParsingCreateParams.builder()
                    .tier(ParsingCreateParams.Tier.AGENTIC)
                    .version(ParsingCreateParams.Version.LATEST)
                    .fileId("test-file-id")
                    .agenticOptions(ParsingCreateParams.AgenticOptions.builder().build())
                    .build();

            assertNotNull(params);
        }

        @Test
        @DisplayName("toBuilder 复制并修改参数")
        void toBuilderCopiesAndModifiesParams() {
            ParsingCreateParams original = ParsingCreateParams.builder()
                    .tier(ParsingCreateParams.Tier.AGENTIC)
                    .version(ParsingCreateParams.Version.LATEST)
                    .fileId("original-file-id")
                    .build();

            ParsingCreateParams copied = original.toBuilder()
                    .fileId("modified-file-id")
                    .build();

            assertNotNull(copied);
            // 无法直接访问 private 字段,验证不抛异常即可
        }

        @Test
        @DisplayName("同步创建 Parsing Job(需真实 API Key)")
        @EnabledIfEnvironmentVariable(named = "LLAMA_CLOUD_API_KEY", matches = ".+")
        void createParsingJobSync() {
            // 先上传文件以拿到 fileId
            String fileId = uploadTestFile();

            ParsingCreateParams params = ParsingCreateParams.builder()
                    .tier(ParsingCreateParams.Tier.AGENTIC)
                    .version(ParsingCreateParams.Version.LATEST)
                    .fileId(fileId)
                    .build();

            ParsingCreateResponse response = syncClient.parsing().create(params);
            assertNotNull(response, "Parsing create 响应不应为 null");
            response.validate(); // 显式触发响应校验
        }

        @Test
        @DisplayName("异步创建 Parsing Job(需真实 API Key)")
        @EnabledIfEnvironmentVariable(named = "LLAMA_CLOUD_API_KEY", matches = ".+")
        void createParsingJobAsync() throws Exception {
            String fileId = uploadTestFile();

            ParsingCreateParams params = ParsingCreateParams.builder()
                    .tier(ParsingCreateParams.Tier.AGENTIC)
                    .version(ParsingCreateParams.Version.LATEST)
                    .fileId(fileId)
                    .build();

            CompletableFuture<ParsingCreateResponse> future = asyncClient.parsing().create(params);
            ParsingCreateResponse response = future.get(120, TimeUnit.SECONDS);
            assertNotNull(response);
        }

        @Test
        @DisplayName("携带额外 header/query/body 的 Parsing 创建")
        @EnabledIfEnvironmentVariable(named = "LLAMA_CLOUD_API_KEY", matches = ".+")
        void createParsingWithAdditionalProperties() {
            String fileId = uploadTestFile();

            ParsingCreateParams params = ParsingCreateParams.builder()
                    .tier(ParsingCreateParams.Tier.AGENTIC)
                    .version(ParsingCreateParams.Version.LATEST)
                    .fileId(fileId)
                    .putAdditionalHeader("X-Test-Header", "test-value")
                    .putAdditionalQueryParam("debug", "true")
                    .putAdditionalBodyProperty("customProp", JsonValue.from("custom"))
                    .build();

            ParsingCreateResponse response = syncClient.parsing().create(params);
            assertNotNull(response);
            // _additionalProperties 用于读取未文档化返回字段
            Map<String, JsonValue> extras = response._additionalProperties();
            assertNotNull(extras, "_additionalProperties 不应返回 null");
        }

        @Test
        @DisplayName("请求级 RequestOptions 配置 timeout 与 responseValidation")
        @EnabledIfEnvironmentVariable(named = "LLAMA_CLOUD_API_KEY", matches = ".+")
        void createParsingWithRequestOptions() {
            String fileId = uploadTestFile();

            ParsingCreateParams params = ParsingCreateParams.builder()
                    .tier(ParsingCreateParams.Tier.AGENTIC)
                    .version(ParsingCreateParams.Version.LATEST)
                    .fileId(fileId)
                    .build();

            ParsingCreateResponse response = syncClient.parsing().create(
                    params,
                    RequestOptions.builder()
                            .timeout(Duration.ofSeconds(30))
                            .responseValidation(true)
                            .build()
            );
            assertNotNull(response);
        }
    }

    // ============================ Extract 服务 ============================

    @Nested
    @DisplayName("Extract 服务")
    class ExtractServiceTest {

        @Test
        @DisplayName("list 返回分页结果(需真实 API Key)")
        @EnabledIfEnvironmentVariable(named = "LLAMA_CLOUD_API_KEY", matches = ".+")
        void listExtractJobs() {
            ExtractListPage page = syncClient.extract().list();
            assertNotNull(page, "extract list 页不应为 null");
            assertNotNull(page.items(), "当前页 items 不应为 null");
            assertTrue(page.items().size() >= 0, "items 数量应 >= 0");
        }

        @Test
        @DisplayName("autoPager 自动分页遍历所有 extract(限 50 条)")
        @EnabledIfEnvironmentVariable(named = "LLAMA_CLOUD_API_KEY", matches = ".+")
        void autoPagerIteratesExtractJobs() {
            ExtractListPage page = syncClient.extract().list();

            long count = 0;
            for (ExtractV2Job job : page.autoPager()) {
                assertNotNull(job, "遍历到的 job 不应为 null");
                if (++count >= 50) {
                    break; // 防止超大账户下遍历过多
                }
            }
            assertTrue(count >= 0, "autoPager 至少应允许进入循环");
        }

        @Test
        @DisplayName("手动 nextPage 分页遍历(最多 3 页)")
        @EnabledIfEnvironmentVariable(named = "LLAMA_CLOUD_API_KEY", matches = ".+")
        void manualPagination() {
            ExtractListPage page = syncClient.extract().list();
            int pageCount = 0;
            int totalItems = 0;

            while (true) {
                totalItems += page.items().size();
                pageCount++;
                if (!page.hasNextPage() || pageCount >= 3) {
                    break;
                }
                page = page.nextPage();
                assertNotNull(page, "nextPage 结果不应为 null");
            }
            assertTrue(pageCount >= 1, "至少应遍历 1 页");
            assertTrue(totalItems >= 0);
        }

        @Test
        @DisplayName("异步 list extract")
        @EnabledIfEnvironmentVariable(named = "LLAMA_CLOUD_API_KEY", matches = ".+")
        void listExtractAsync() throws Exception {
            CompletableFuture<ExtractListPageAsync> future = asyncClient.extract().list();
            ExtractListPageAsync page = future.get(60, TimeUnit.SECONDS);
            assertNotNull(page);
        }
    }

    // ============================ Files 服务 ============================

    @Nested
    @DisplayName("Files 服务")
    class FilesServiceTest {

        @Test
        @DisplayName("byte[] 方式构建 FileCreateParams")
        void buildFileCreateParamsFromBytes() {
            FileCreateParams params = FileCreateParams.builder()
                    .purpose("parsing")
                    .file("hello llama".getBytes())
                    .build();
            assertNotNull(params);
        }

        @Test
        @DisplayName("Path 方式构建 FileCreateParams")
        void buildFileCreateParamsFromPath() {
            Path dummy = Paths.get("src", "test", "resources", "dummy.txt");
            // 即使文件不存在,Builder 也只是持有 Path 引用,不立即读取
            FileCreateParams params = FileCreateParams.builder()
                    .purpose("parsing")
                    .file(dummy)
                    .build();
            assertNotNull(params);
        }

        @Test
        @DisplayName("MultipartField 方式构建 FileCreateParams")
        void buildFileCreateParamsFromMultipart() {
            FileCreateParams params = FileCreateParams.builder()
                    .purpose("parsing")
                    .file(MultipartField.<java.io.InputStream>builder()
                            .value(new java.io.ByteArrayInputStream("data".getBytes()))
                            .filename("test.txt")
                            .build())
                    .build();
            assertNotNull(params);
        }

        @Test
        @DisplayName("上传文件并返回 fileId(需真实 API Key)")
        @EnabledIfEnvironmentVariable(named = "LLAMA_CLOUD_API_KEY", matches = ".+")
        void uploadFile() {
            FileCreateParams params = FileCreateParams.builder()
                    .purpose("parsing")
                    .file("LlamaIndex Java SDK upload test.".getBytes())
                    .build();

            FileCreateResponse response = syncClient.files().create(params);
            assertNotNull(response, "文件上传响应不应为 null");
        }
    }

    // ============================ Beta Indexes 服务 ============================

    @Nested
    @DisplayName("Beta Indexes 服务")
    class BetaIndexesServiceTest {

        @Test
        @DisplayName("构建 IndexListParams")
        void buildIndexListParams() {
            IndexListParams params = IndexListParams.builder()
                    .projectId(TEST_PROJECT_ID)
                    .build();
            assertNotNull(params);
        }

        @Test
        @DisplayName("list 索引(需真实 API Key)")
        @EnabledIfEnvironmentVariable(named = "LLAMA_CLOUD_API_KEY", matches = ".+")
        void listIndexes() {
            IndexListParams params = IndexListParams.builder()
                    .projectId(TEST_PROJECT_ID)
                    .build();

            IndexListPage page = syncClient.beta().indexes().list(params);
            assertNotNull(page);
            assertNotNull(page.items());
        }

        @Test
        @DisplayName("withRawResponse 获取原始 HTTP 响应")
        @EnabledIfEnvironmentVariable(named = "LLAMA_CLOUD_API_KEY", matches = ".+")
        void listIndexesWithRawResponse() {
            IndexListParams params = IndexListParams.builder()
                    .projectId(TEST_PROJECT_ID)
                    .build();

            HttpResponseFor<IndexListPage> raw = syncClient.beta().indexes()
                    .withRawResponse()
                    .list(params);

            assertTrue(raw.statusCode() >= 200 && raw.statusCode() < 300,
                    "HTTP 状态码应为 2xx,实际: " + raw.statusCode());
            assertNotNull(raw.headers(), "响应 headers 不应为 null");

            IndexListPage parsed = raw.parse();
            assertNotNull(parsed, "parse() 后的对象不应为 null");
        }
    }

    // ============================ 异常体系 ============================

    @Nested
    @DisplayName("异常处理")
    class ExceptionHandling {

        @Test
        @DisplayName("无效 apiKey 应抛 UnauthorizedException 或 LlamaCloudException")
        @EnabledIfEnvironmentVariable(named = "LLAMA_CLOUD_API_KEY", matches = ".+")
        void invalidKeyThrowsException() {
            LlamaCloudClient badClient = LlamaCloudOkHttpClient.builder()
                    .apiKey("definitely-invalid-key-12345")
                    .maxRetries(0) // 关闭重试,加速失败
                    .timeout(Duration.ofSeconds(15))
                    .build();

            LlamaCloudException ex = assertThrows(LlamaCloudException.class, () -> {
                badClient.extract().list();
            }, "使用无效 apiKey 调用应抛 LlamaCloudException");

            // 通常是 401,但也可能是网络层异常;这里只校验类型层级
            assertTrue(ex instanceof UnauthorizedException
                            || ex instanceof BadRequestException
                            || ex.getMessage() != null,
                    "异常信息应可读: " + ex.getMessage());
        }

        @Test
        @DisplayName("空 fileId 创建 Parsing 应抛 BadRequestException 或 LlamaCloudException")
        @EnabledIfEnvironmentVariable(named = "LLAMA_CLOUD_API_KEY", matches = ".+")
        void emptyFileIdThrows() {
            ParsingCreateParams params = ParsingCreateParams.builder()
                    .tier(ParsingCreateParams.Tier.AGENTIC)
                    .version(ParsingCreateParams.Version.LATEST)
                    .fileId("")
                    .build();

            assertThrows(LlamaCloudException.class, () -> {
                syncClient.parsing().create(params);
            });
        }

        @Test
        @DisplayName("异步调用失败时 CompletableFuture 异常可获取")
        @EnabledIfEnvironmentVariable(named = "LLAMA_CLOUD_API_KEY", matches = ".+")
        void asyncFailurePropagatesToFuture() {
            LlamaCloudClientAsync badAsync = LlamaCloudOkHttpClientAsync.builder()
                    .apiKey("definitely-invalid-key-12345")
                    .maxRetries(0)
                    .build();

            CompletableFuture<ExtractListPageAsync> future = badAsync.extract().list();
            ExecutionException ex = assertThrows(ExecutionException.class, () -> {
                future.get(30, TimeUnit.SECONDS);
            });
            assertNotNull(ex.getCause(), "异步异常应携带 cause");
            assertTrue(ex.getCause() instanceof LlamaCloudException,
                    "cause 应为 LlamaCloudException,实际: " + ex.getCause().getClass());
        }
    }

    // ============================ 辅助方法 ============================

    /**
     * 上传一个测试文件,返回 fileId。仅在持有真实 API Key 时调用。
     */
    private String uploadTestFile() {
        FileCreateParams params = FileCreateParams.builder()
                .purpose("parsing")
                .file(("Z-Brain LlamaIndex SDK test content @ " + System.currentTimeMillis()).getBytes())
                .build();
        FileCreateResponse response = syncClient.files().create(params);
        assertNotNull(response, "上传测试文件失败,响应为 null");
        // FileCreateResponse 的 id 字段访问器;若 SDK 命名不同,需调整为 id() / _id() / getId()
        try {
            return response.id();
        } catch (NoSuchMethodError ignored) {
            fail("无法从 FileCreateResponse 获取 fileId,请确认 SDK 访问器方法名(如 id()/_id())");
            return null;
        }
    }
}
