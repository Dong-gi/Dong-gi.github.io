package io.github.donggi.bean;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello15Test4 {
    @Test
    public void test() throws Exception {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans21.xml")) {
            log.info(context.getBean("hello15"));
            Thread.sleep(1000);
            log.info(context.getBean("hello15"));
            Thread.sleep(1000);
            log.info(context.getBean("hello15"));
        }
    }
/*
18:03:41.778 [main] INFO io.github.donggi.bean.Hello15Test4 - Hello15(hello16=Hello16(message=Wed Jun 10 18:03:41 KST 2020))
18:03:42.778 [main] INFO io.github.donggi.bean.Hello15Test4 - Hello15(hello16=Hello16(message=Wed Jun 10 18:03:42 KST 2020))
18:03:43.779 [main] INFO io.github.donggi.bean.Hello15Test4 - Hello15(hello16=Hello16(message=Wed Jun 10 18:03:43 KST 2020))
*/
}
