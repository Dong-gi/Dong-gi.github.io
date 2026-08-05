package io.github.donggi.bean;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello20Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans34.xml");) {
            var c1 = (Hello20) context.getBean("c1");
            var c2 = (Hello20) context.getBean("c2");
            var c3 = (Hello20) context.getBean("c3");
            log.info(c1);
            log.info(c2);
            log.info(c3);
            assertTrue(c1.getCreated() > c3.getCreated());
            assertTrue(c3.getCreated() > c2.getCreated());
        }
    }
/*
14:53:12.958 [main] INFO io.github.donggi.bean.Hello20Test - Hello20(created=1591768392825)
14:53:12.958 [main] INFO io.github.donggi.bean.Hello20Test - Hello20(created=1591768392596)
14:53:12.958 [main] INFO io.github.donggi.bean.Hello20Test - Hello20(created=1591768392723)
*/
}
