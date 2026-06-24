package cn.kong.zbrain;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智多星知识库系统 (Z-Brain) 主启动类
 *
 * <p>基于 Spring Boot 3.4.5 + Spring AI 1.1.8 + 阿里云百炼平台，
 * 提供企业级 RAG 知识库问答能力。</p>
 *
 * @author zbrain-team
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@MapperScan("cn.kong.zbrain.mapper")
public class ZBrainApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZBrainApplication.class, args);
        System.out.println("""

                ====================================================
                  智多星知识库系统 (Z-Brain) 启动成功
                  Z-Brain Knowledge Base System Started
                ====================================================
                """);
    }
}
