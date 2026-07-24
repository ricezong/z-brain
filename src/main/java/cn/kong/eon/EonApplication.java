package cn.kong.eon;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 灵犀 LingXi 主启动类（新工程设计方案 N0）
 *
 * <p>领域驱动分包：agent / rag / llm / event / config / knowledge / persistence / common</p>
 *
 * @author eon-team
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@MapperScan("cn.kong.eon.persistence.mapper")
public class EonApplication {

    public static void main(String[] args) {
        SpringApplication.run(EonApplication.class, args);
        System.out.println("""

                ====================================================
                  元 Eon 启动成功
                  Eon Agent System Started
                ====================================================
                """);
    }
}
