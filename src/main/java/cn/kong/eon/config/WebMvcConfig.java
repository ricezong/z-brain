package cn.kong.eon.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 *
 * <p>配置 CORS 跨域、SSE 流式响应支持等。</p>
 * <p>字符编码由 server.servlet.encoding.force=true 在 application.yml 中统一配置。</p>
 *
 * @author eon-team
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition", "X-Progress-Id")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
