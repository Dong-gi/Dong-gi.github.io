package io.github.donggi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.format.support.FormattingConversionService;

import io.github.donggi.anno.MyFormatAnnotationFormatterFactory;

@Configuration
public class FormatConfig {
    @Bean
    public FormattingConversionService conversionService() {
        var service = new DefaultFormattingConversionService();

        service.addFormatterForFieldAnnotation(new MyFormatAnnotationFormatterFactory());
        service.addFormatter(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return service;
    }
}
