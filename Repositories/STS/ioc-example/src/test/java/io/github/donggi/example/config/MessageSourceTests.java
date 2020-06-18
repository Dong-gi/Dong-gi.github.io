package io.github.donggi.example.config;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import io.github.donggi.example.config.I18nConfig;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
class MessageSourceTests {
    @Test
    void test() {
        try (var context = new AnnotationConfigApplicationContext(I18nConfig.class)) {
            log.info(context.getMessage("apple", null, Locale.KOREAN));
        }
    }
}
