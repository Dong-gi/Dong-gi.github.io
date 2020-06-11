package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello25Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans40.xml")) {
            log.info("Nothing happened...");
        }
    }
/*
17:12:34.732 [main] INFO io.github.donggi.bean.Hello25 - Hello World! You made me!
17:12:34.764 [main] INFO io.github.donggi.bean.Hello25Test - Nothing happened...
*/
}
