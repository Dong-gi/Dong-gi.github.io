package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello29Test {

    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans46.xml")) {
            log.info(context.getBean("hello29"));
        }
    }
/*
17:35:42.441 [main] INFO io.github.donggi.bean.Hello29Test - Hello29(message=Hello29 - 메시지)
17:35:42.452 [main] INFO io.github.donggi.bean.Hello29 - Hello29 Bean isRunning? true
17:35:42.452 [main] INFO io.github.donggi.bean.Hello29 - Hello29 Bean Stopped...
*/
}
