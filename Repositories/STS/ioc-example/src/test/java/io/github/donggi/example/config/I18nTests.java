package io.github.donggi.example.config;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
class I18nTests {

    @Autowired
    private ApplicationContext context;
    @Test
    void test() {
        log.info(context.getMessage("hello.user", new Object[] { "User Name" }, Locale.ENGLISH));
        log.info(context.getMessage("hello.user", new Object[] { "유저 이름" }, Locale.KOREAN));
        log.info(context.getMessage("blue", null, Locale.ENGLISH));
        log.info(context.getMessage("blue", null, Locale.KOREAN));
    }
/*
11:22:36.201 [main] INFO  i.g.donggi.example.config.I18nTests - Hello {User Name}!
11:22:36.211 [main] INFO  i.g.donggi.example.config.I18nTests - "유저 이름'님 안녕하세요!
11:22:36.214 [main] INFO  i.g.donggi.example.config.I18nTests - blue
11:22:36.215 [main] INFO  i.g.donggi.example.config.I18nTests - 파랑
*/
}
