package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello15Test {
    @Test
    public void test() throws Exception {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans20.xml")) {
            log.info(context.getBean("hello15"));
            Thread.sleep(1000);
            log.info(context.getBean("hello15"));
            Thread.sleep(1000);
            log.info(context.getBean("hello15"));
        }
    }
/*
18:03:12.116 [main] INFO io.github.donggi.bean.Hello15Test - Hello15(hello16=Hello16(message=Wed Jun 10 18:03:11 KST 2020))
18:03:13.118 [main] INFO io.github.donggi.bean.Hello15Test - Hello15(hello16=Hello16(message=Wed Jun 10 18:03:11 KST 2020))
18:03:14.118 [main] INFO io.github.donggi.bean.Hello15Test - Hello15(hello16=Hello16(message=Wed Jun 10 18:03:11 KST 2020))
*/
}
