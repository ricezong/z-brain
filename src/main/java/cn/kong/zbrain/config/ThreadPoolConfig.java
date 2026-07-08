package cn.kong.zbrain.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 多线程池配置
 *
 * <p>方案核心设计：通过线程池隔离资源，防止相互阻塞。</p>
 * <ul>
 *   <li><b>解析池 (parseExecutor)</b>：处理 CPU/IO 密集型任务（文档解析、Tika 抽取）</li>
 *   <li><b>向量化池 (embeddingExecutor)</b>：处理强 IO 依赖任务（调用百炼 SDK）</li>
 *   <li><b>检索池 (retrievalExecutor)</b>：并行执行多路召回（向量 + 全文），隔离检索耗时</li>
 *   <li><b>流式对话池 (sseStreamExecutor)</b>：异步执行 SseEmitter 流式问答，保证事件实时推送</li>
 * </ul>
 *
 * @author zbrain-team
 */
@Configuration
public class ThreadPoolConfig {

    /**
     * 文档解析线程池
     * <p>用于 Tika 解析、文本清洗、父子分块等 CPU/IO 密集型任务。</p>
     */
    @Bean("parseExecutor")
    public Executor parseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("zbrain-parse-");
        // 队列满时由调用线程执行，避免任务丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅关闭
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * 向量化线程池
     * <p>用于调用百炼 SDK 进行 Embedding、Rerank 等强 IO 依赖任务。</p>
     */
    @Bean("embeddingExecutor")
    public Executor embeddingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("zbrain-embed-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        return executor;
    }

    /**
     * 检索线程池
     * <p>用于并行执行多路召回（向量检索 + 全文检索），每路召回占用一个线程。</p>
     * <p>线程数需与 HikariCP 连接池配合：每个检索线程最多占用 1 个 DB 连接，
     * 峰值并发 = corePoolSize，不应超过 HikariCP maximum-pool-size 的一半。</p>
     */
    @Bean("retrievalExecutor")
    public Executor retrievalExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("zbrain-retrieval-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * SSE 流式对话线程池
     * <p>用于异步执行流式问答（SseEmitter），使 Controller 方法立即返回，
     * 保证 SSE 事件逐条实时推送而非缓冲后一次性刷新。</p>
     * <p>任务为长耗时 IO 阻塞型（等待 LLM 逐 token 返回），
     * 每个并发对话占用一个线程，线程数即最大并发对话数。</p>
     */
    @Bean("sseStreamExecutor")
    public Executor sseStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("zbrain-sse-stream-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(300);
        executor.initialize();
        return executor;
    }

    /**
     * 通用异步任务线程池
     * <p>用于日志沉淀、进度更新等辅助任务。</p>
     */
    @Bean("asyncExecutor")
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("zbrain-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.initialize();
        return executor;
    }
}
