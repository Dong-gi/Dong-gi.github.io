package io.github.donggi.bean;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello19Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans33.xml");) {
            log.info(context.getBean("hello19"));
        }
    }
/*
14:27:18.904 [main] INFO io.github.donggi.bean.Hello19Test - Hello19(foo=Hello19.Nested1(bar=Hello19.Nested2(msg=Hello World)))
*/
}
