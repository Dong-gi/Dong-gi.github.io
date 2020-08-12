package io.github.donggi.mvc.config;

import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import io.github.donggi.mvc.interceptor.CustomInterceptor;
import lombok.extern.slf4j.Slf4j;

@ComponentScan(basePackages = "io.github.donggi.mvc.controller")
@Configuration
@EnableWebMvc
@Slf4j
public class AppConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LocaleChangeInterceptor()).order(Ordered.LOWEST_PRECEDENCE);
        registry.addInterceptor(new CustomInterceptor()).addPathPatterns("/hello");
        log.debug("Hello");
    }
    @Bean
    public static LocaleResolver localeResolver()
    {
        var localeResolver = new SessionLocaleResolver();
        localeResolver.setDefaultLocale(Locale.KOREAN);
        return localeResolver;
    }
    @Bean
    public static String defaultMsg() {
        return "기본 메시지";
    }
}