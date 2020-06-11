package io.github.donggi.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
    @Bean
    public static MinuteBeanFactoryPostProcessor beanFactoryPostProcessor() {
        return new MinuteBeanFactoryPostProcessor();
    }
}
