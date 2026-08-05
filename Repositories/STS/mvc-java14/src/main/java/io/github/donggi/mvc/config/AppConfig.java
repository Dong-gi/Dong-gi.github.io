package io.github.donggi.mvc.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import io.github.donggi.mvc.util.MultiDateFormatter;

@ComponentScan(basePackages = "io.github.donggi.mvc.aop, io.github.donggi.mvc.controller, io.github.donggi.mvc.service")
@EnableWebMvc
@Configuration
public class AppConfig implements WebMvcConfigurer {
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addFormatter(MultiDateFormatter.of(false, "'Y'yyyy'M'MM'D'dd", "yyyy년MM월dd일"));
    }
}
