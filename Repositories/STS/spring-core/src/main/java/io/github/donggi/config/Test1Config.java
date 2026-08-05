package io.github.donggi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.format.datetime.DateFormatter;
import org.springframework.format.datetime.DateFormatterRegistrar;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.format.support.FormattingConversionService;

import io.github.donggi.anno.Test1FormatAnnotationFormatterFactory;
import io.github.donggi.bean.Test1Bean;

@Profile("test1")
@Configuration
public class Test1Config {
    @Bean
    public FormattingConversionService conversionService() {
        var service = new DefaultFormattingConversionService(false);

        service.addFormatterForFieldAnnotation(new Test1FormatAnnotationFormatterFactory());

        var registrar = new DateFormatterRegistrar();
        registrar.setFormatter(new DateFormatter("yyyyMMddHHmmss"));
        registrar.registerFormatters(service);

        return service;
    }
    @Bean
    public Test1Bean test1Bean() {
        return new Test1Bean();
    }
}