package io.github.donggi.mvc.config;

import javax.servlet.Filter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import io.github.donggi.mvc.interceptor.Controller5Interceptor;

@ComponentScan(basePackages = "io.github.donggi.mvc.aop, io.github.donggi.mvc.controller, io.github.donggi.mvc.service")
@EnableWebMvc
@Configuration
public class AppConfig implements WebMvcConfigurer {
    @Bean
    public Filter characterEncodingFilter() {
        var filter = new CharacterEncodingFilter("UTF-8");
        return filter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LocaleChangeInterceptor()).order(Ordered.LOWEST_PRECEDENCE);
        registry.addInterceptor(new Controller5Interceptor()).addPathPatterns("/5/*");
    }
}
