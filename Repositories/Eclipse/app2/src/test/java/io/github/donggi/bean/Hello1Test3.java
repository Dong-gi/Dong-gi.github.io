package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class Hello1Test3 {

    @Test
    public void test() {
        ApplicationContext context = new FileSystemXmlApplicationContext("src/main/resource/Beans11.xml");
        ((AbstractApplicationContext) context).registerShutdownHook();
        System.out.println(((Hello1) context.getBean("hello1")).getMessage());
    }
/*
22:31:22.901 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'hello1'
기본 메시지
 */
}
