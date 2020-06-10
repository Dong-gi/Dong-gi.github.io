package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello1Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans1.xml");) {
            log.info(context.getBean("hello1"));
        }
    }
/*
13:20:52.089 [main] INFO io.github.donggi.bean.Hello1Test - Hello1(message=Hello1 - 메시지)
*/
}
