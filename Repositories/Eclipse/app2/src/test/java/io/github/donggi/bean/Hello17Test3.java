package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello17Test3 {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans27.xml")) {
            log.info(context.getBean(Hello17.class.getCanonicalName() + "#0"));
        }
    }
/*
13:23:16.416 [main] INFO io.github.donggi.bean.Hello17Test3 - Hello17(message=Hello Anonymous 17)
*/
}
