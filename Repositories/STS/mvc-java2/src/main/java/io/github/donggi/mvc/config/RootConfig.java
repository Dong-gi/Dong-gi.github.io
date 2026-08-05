package io.github.donggi.mvc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RootConfig {
    @Bean
    public static String rootMsg() {
        return "루트 메시지";
    }
}