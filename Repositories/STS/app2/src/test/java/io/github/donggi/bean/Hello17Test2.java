package io.github.donggi.bean;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello17Test2 {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans26.xml")) {
            log.info(context.getBean("hello17"));
        }
    }
/*
13:19:48.173 [main] INFO io.github.donggi.bean.Hello17Test2 - Hello17(message=Hello World 17)
*/
}
