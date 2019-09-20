package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class ConfigTest2 {

    @Test
    public void test() {
        ApplicationContext context = new FileSystemXmlApplicationContext("src/main/resource/Beans22.xml");
        ((AbstractApplicationContext) context).registerShutdownHook();
        System.out.println((Hello1)context.getBean("hello1"));
        System.out.println((Hello1)context.getBean("hello1-2"));
        System.out.println((Hello6)context.getBean("hello6"));
    }
/*
Hello1(message=Hello1 메시지)
Hello1(message=기본 메시지)
Hello6(message=기본 메시지)
 */
}
