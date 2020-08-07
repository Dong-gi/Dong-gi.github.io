package io.github.donggi.bean;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello23Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans38.xml")) {
            log.info(context.getBean("hello23"));
        }
    }
/*
16:27:49.639 [main] INFO io.github.donggi.bean.Hello23Test - Hello23(message=Hello23 - 메시지)
16:27:49.648 [main] DEBUG org.springframework.context.support.FileSystemXmlApplicationContext - Closing org.springframework.context.support.FileSystemXmlApplicationContext@67f639d3, started on Thu Jun 11 16:27:49 KST 2020
Hello3 Bean Destroyed...
*/
}
