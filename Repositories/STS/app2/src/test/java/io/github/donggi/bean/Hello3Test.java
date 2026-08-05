package io.github.donggi.bean;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello3Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans3.xml")) {
            log.info(context.getBean("hello3"));
        }
    }
/*
Hello3 Bean Initialized...
16:23:27.880 [main] INFO io.github.donggi.bean.Hello3Test - Hello3(message=Hello3 - 메시지)
16:23:27.880 [main] DEBUG org.springframework.context.support.FileSystemXmlApplicationContext - Closing org.springframework.context.support.FileSystemXmlApplicationContext@1f59a598, started on Thu Jun 11 16:23:27 KST 2020
Hello3 Bean Destroyed...
 */
}
