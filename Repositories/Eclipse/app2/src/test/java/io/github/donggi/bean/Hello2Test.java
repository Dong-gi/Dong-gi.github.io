package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello2Test {

    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans2.xml")) {
            log.info(context.getBean("hello2"));
        }
    }
/*
Hello2 Bean Initialized...
16:14:41.601 [main] INFO io.github.donggi.bean.Hello2Test - Hello2(message=Hello2 - 메시지)
16:14:41.609 [main] DEBUG org.springframework.context.support.FileSystemXmlApplicationContext - Closing org.springframework.context.support.FileSystemXmlApplicationContext@67f639d3, started on Thu Jun 11 16:14:40 KST 2020
Hello2 Bean Destroyed...
*/
}
