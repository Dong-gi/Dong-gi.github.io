package io.github.donggi.bean;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello1Test3 {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans11.xml")) {
            log.info(context.getBean("hello1"));
        }
    }
/*
14:01:11.834 [main] INFO io.github.donggi.bean.Hello1Test3 - Hello1(message=기본 메시지)
*/
}
