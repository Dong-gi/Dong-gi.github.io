package io.github.donggi.bean;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello1Test2 {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans5.xml")) {
            log.info(context.getBean("hello1"));
        }
    }
/*
13:21:44.479 [main] INFO io.github.donggi.bean.Hello1Test2 - Hello1(message=Hello1 - 메시지)
*/
}
