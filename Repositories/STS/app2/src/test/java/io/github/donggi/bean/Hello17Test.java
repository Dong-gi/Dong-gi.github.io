package io.github.donggi.bean;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello17Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans25.xml")) {
            log.info(context.getBean("hello17-1"));
            log.info(context.getBean("hello17-2"));
        }
    }
/*
13:19:04.043 [main] INFO io.github.donggi.bean.Hello17Test - Hello17(message=Hello 17 and 1)
13:19:04.043 [main] INFO io.github.donggi.bean.Hello17Test - Hello17(message=Hello 17 and 2)
*/
}
