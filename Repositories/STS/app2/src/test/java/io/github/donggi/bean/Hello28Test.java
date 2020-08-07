package io.github.donggi.bean;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello28Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans45.xml")) {
            log.info(context.getBean("hello28"));
            log.info(context.getBean("hello28"));
            log.info(context.getBean("&hello28"));
        }
    }
/*
13:59:17.439 [main] INFO io.github.donggi.bean.Hello28Test - Hello28(message=Hello@249952447919300)
13:59:17.440 [main] INFO io.github.donggi.bean.Hello28Test - Hello28(message=Hello@249952448939400)
13:59:17.440 [main] INFO io.github.donggi.bean.Hello28Test - io.github.donggi.bean.Hello28$Hello28Factory@2421cc4
*/
}
