package cn.kong.zbrain;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 应用启动测试
 *
 * @author zbrain-team
 */
@SpringBootTest
@ActiveProfiles("dev")
class ZBrainApplicationTests {

    @Test
    void contextLoads() {
        // 验证 Spring 上下文能够正常加载
    }
}
