package io.github.donggi.mvc.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@ComponentScan(basePackages = "io.github.donggi.mvc.aop, io.github.donggi.mvc.controller, io.github.donggi.mvc.service, io.github.donggi.mvc.ws")
@EnableWebMvc
@Configuration
public class AppConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**").addResourceLocations("/static").setCachePeriod(31556926);
    }
}
