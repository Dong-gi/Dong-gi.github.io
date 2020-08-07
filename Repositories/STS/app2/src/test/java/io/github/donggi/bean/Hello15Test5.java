package io.github.donggi.bean;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello15Test5 {
    @Test
    public void test() throws Exception {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans37.xml")) {
            log.info(context.getBean("hello15"));
            Thread.sleep(1000);
            log.info(context.getBean("hello15"));
            Thread.sleep(1000);
            log.info(context.getBean("hello15"));
        }
    }
/*
18:04:18.524 [main] INFO io.github.donggi.bean.Hello15Test5 - Hello15(hello16=Hello16(message=Wed Jun 10 18:04:18 KST 2020))
18:04:19.524 [main] INFO io.github.donggi.bean.Hello15Test5 - Hello15(hello16=Hello16(message=Wed Jun 10 18:04:19 KST 2020))
18:04:20.524 [main] INFO io.github.donggi.bean.Hello15Test5 - Hello15(hello16=Hello16(message=Wed Jun 10 18:04:20 KST 2020))
*/
}
