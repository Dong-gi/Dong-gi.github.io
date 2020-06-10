package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello15Test3 {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans12.xml")) {
            log.info(context.getBean("hello15-1"));
            log.info(context.getBean("hello15-2"));
            log.info(context.getBean("hello15-3"));
        }
    }
/*
16:11:31.213 [main] INFO io.github.donggi.bean.Hello15Test3 - Hello15(hello16=Hello16(message=Overrided Message))
16:11:31.213 [main] INFO io.github.donggi.bean.Hello15Test3 - Hello15(hello16=Hello16(message=Wed Jun 10 16:11:31 KST 2020))
16:11:31.213 [main] INFO io.github.donggi.bean.Hello15Test3 - Hello15(hello16=null)
*/
}
