package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello17Test4 {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans32.xml")) {
            log.info(context.getBean("hello17-1"));
            log.info(context.getBean("hello17-2"));
        }
    }
/*
14:11:51.298 [main] INFO io.github.donggi.bean.Hello17Test4 - Hello17(message=Hello World 17)
14:11:51.298 [main] INFO io.github.donggi.bean.Hello17Test4 - Hello17(message=Default Message)
*/
}
