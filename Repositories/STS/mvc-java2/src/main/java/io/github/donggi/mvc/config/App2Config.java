package io.github.donggi.mvc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@ComponentScan(basePackages = "io.github.donggi.mvc.app2.controller")
@Configuration
public class App2Config {
    @Bean
    public static String app2Msg() {
        return "App2 메시지";
    }
}
