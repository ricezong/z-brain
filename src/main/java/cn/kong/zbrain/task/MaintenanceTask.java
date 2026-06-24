package cn.kong.zbrain.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时维护任务
 *
 * <p>业务低峰期执行 ANALYZE 更新统计信息，保障 ivfflat 索引在数据量增长后的召回率。</p>
 *
 * @author zbrain-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaintenanceTask {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 每天凌晨 3 点执行 ANALYZE kb_chunk
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void analyzeKbChunk() {
        try {
            log.info("开始执行 ANALYZE kb_chunk 维护任务...");
            long start = System.currentTimeMillis();
            jdbcTemplate.execute("ANALYZE kb_chunk;");
            long cost = System.currentTimeMillis() - start;
            log.info("ANALYZE kb_chunk 完成，耗时: {}ms", cost);
        } catch (Exception e) {
            log.error("ANALYZE kb_chunk 失败", e);
        }
    }

    /**
     * 每天凌晨 4 点清理过期的对话上下文（Redis TTL 已自动清理，此处兜底）
     */
    @Scheduled(cron = "0 0 4 * * ?")
    public void cleanupExpiredSessions() {
        try {
            log.info("开始清理过期会话...");
            // 此处可补充清理逻辑，如关闭超过 7 天未活动的会话
            log.info("过期会话清理完成");
        } catch (Exception e) {
            log.error("清理过期会话失败", e);
        }
    }
}
