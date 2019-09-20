package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class Hello10Test {

    @Test
    public void test() {
        ApplicationContext context = new FileSystemXmlApplicationContext("src/main/resource/Beans18.xml");
        ((AbstractApplicationContext) context).registerShutdownHook();
        System.out.println(((Hello10) context.getBean("hello10")).getMessage());
        System.out.println(((Hello11) context.getBean("hello11")).getMessage());
        System.out.println(((Hello12) context.getBean("hello12")).getMessage());
        System.out.println(((Hello13) context.getBean("hello13")).getMessage());
        System.out.println(((Hello14) context.getBean("olleh14")).getMessage());
    }
/*
20:43:17.620 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'hello10'
20:43:17.623 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'hello11'
20:43:17.624 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'hello12'
20:43:17.624 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'hello13'
20:43:17.625 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'olleh14'
Hello10 기본 메시지
Hello11 기본 메시지
Hello12 기본 메시지
Hello14 기본 메시지
Hello14 기본 메시지
 */
}
