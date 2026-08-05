package io.github.donggi.mvc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@ComponentScan(basePackages = "io.github.donggi.mvc.app1.controller")
@Configuration
public class App1Config {
    @Bean
    public static String app1Msg() {
        return "App1 메시지";
    }
}