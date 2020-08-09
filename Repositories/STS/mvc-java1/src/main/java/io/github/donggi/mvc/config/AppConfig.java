package io.github.donggi.mvc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@ComponentScan(basePackages = "io.github.donggi.mvc.controller")
@Configuration
public class AppConfig {
    @Bean
    public static String defaultMsg() {
        return "기본 메시지";
    }
}
