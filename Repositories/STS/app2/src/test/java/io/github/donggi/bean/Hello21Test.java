package io.github.donggi.bean;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello21Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans35.xml");) {
            log.info("↑ 'h2' bean already instanciated");
            var h2 = (Hello21) context.getBean("h2");
            log.info(h2);
            var h1 = (Hello21) context.getBean("h1");
            log.info(h1);
            assertTrue(h1.getCreated() > h2.getCreated());
        }
    }
/*
15:15:55.375 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'h2'
15:15:55.444 [main] INFO io.github.donggi.bean.Hello21Test - ↑ 'h2' bean already instantiated
15:15:55.445 [main] INFO io.github.donggi.bean.Hello21Test - Hello21(created=1591769755375)
15:15:55.445 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'h1'
15:15:55.445 [main] INFO io.github.donggi.bean.Hello21Test - Hello21(created=1591769755445)
*/
}
