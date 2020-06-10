package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello18Test {
    @Test
    public void test() throws IllegalArgumentException, NoSuchMethodException, SecurityException, Throwable {
        try (
                var parentContext = new FileSystemXmlApplicationContext("src/main/resource/Beans29.xml");
                var childContext = new FileSystemXmlApplicationContext(parentContext);)
        {
            childContext.setConfigLocation("src/main/resource/Beans30.xml");
            childContext.refresh();

            log.info(parentContext.getBean("hello18"));

            var hello18i = (Hello18)childContext.getBean("hello18");
            log.info(hello18i.getMsg1());
        }
    }
/*
14:03:32.663 [main] INFO io.github.donggi.bean.Hello18Test - Hello18Impl(msg1=Default Message 1, msg2=Default Message 2)
14:03:32.671 [main] INFO io.github.donggi.bean.Hello18Test - Default Message 1
 */
}
