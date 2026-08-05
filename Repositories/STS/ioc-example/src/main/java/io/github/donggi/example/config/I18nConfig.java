package io.github.donggi.example.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

@Configuration
public class I18nConfig {
    @Bean
    public MessageSource messageSource() {
        var source = new ResourceBundleMessageSource();
        source.setDefaultEncoding("UTF-8");
        source.setBasenames("i18n/templates", "i18n/colors");
        /* try various...
        source.setBasenames("/i18n/names",
                "i18n/names",
                "/names",
                "names",
                "file:/i18n/names",
                "file:i18n/names",
                "file:/names",
                "file:names",
                "classpath:/i18n/names",
                "classpath:i18n/names",
                "classpath:/names",
                "classpath:names",
                "classpath*:/i18n/names",
                "classpath*:i18n/names",
                "classpath*:/names",
                "classpath*:names"); */
        return source;
    }
}
