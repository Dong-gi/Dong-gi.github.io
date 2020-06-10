package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello4Test3 {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans7.xml")) {
            log.info(context.getBean("hello1-1"));
            log.info(context.getBean("hello1-2"));
            log.info(context.getBean("hello1-3"));
            log.info(context.getBean("hello17-1"));
            log.info(context.getBean("hello17-2"));
            log.info(context.getBean("hello17-3"));
        }
    }
/*
13:55:02.141 [main] INFO io.github.donggi.bean.Hello4Test3 - Hello1(message=기본 메시지)
13:55:02.141 [main] INFO io.github.donggi.bean.Hello4Test3 - Hello1(message=기본 메시지)
13:55:02.141 [main] INFO io.github.donggi.bean.Hello4Test3 - Hello1(message=빈 메시지 2)
13:55:02.141 [main] INFO io.github.donggi.bean.Hello4Test3 - Hello17(message=기본 메시지)
13:55:02.141 [main] INFO io.github.donggi.bean.Hello4Test3 - Hello17(message=기본 메시지)
13:55:02.141 [main] INFO io.github.donggi.bean.Hello4Test3 - Hello17(message=빈 메시지 3)
*/
}
