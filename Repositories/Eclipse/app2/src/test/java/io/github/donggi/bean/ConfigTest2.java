package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class ConfigTest2 {

    @Test
    public void test() {
        ApplicationContext context = new FileSystemXmlApplicationContext("src/main/resource/Beans22.xml");
        ((AbstractApplicationContext) context).registerShutdownHook();
        log.info((Hello1)context.getBean("hello1"));
        log.info((Hello1)context.getBean("hello1-2"));
        log.info((Hello6)context.getBean("hello6"));
    }
/*
Hello1(message=Hello1 메시지)
Hello1(message=기본 메시지)
Hello6(message=기본 메시지)
 */
}
