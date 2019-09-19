package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class Hello4Test4 {

    @Test
    public void test() {
        ApplicationContext context = new FileSystemXmlApplicationContext("src/main/resource/Beans12.xml");
        ((AbstractApplicationContext) context).registerShutdownHook();
        System.out.println(((Hello4) context.getBean("hello4")).getMessage());
    }
/*
19:24:05.980 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Autowiring by type from bean name 'hello4' via constructor to bean named 'java.lang.String#0'
기본 메시지
 */
}
