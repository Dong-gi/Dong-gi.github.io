package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class Hello4Test3 {

    @Test
    public void test() {
        ApplicationContext context = new FileSystemXmlApplicationContext("src/main/resource/Beans7.xml");
        ((AbstractApplicationContext) context).registerShutdownHook();
        System.out.println(((Hello1) context.getBean("hello1-1")).getMessage());
        System.out.println(((Hello1) context.getBean("hello1-2")).getMessage());
        System.out.println(((Hello4) context.getBean("hello4-1")).getMessage());
        System.out.println(((Hello4) context.getBean("hello4-2")).getMessage());
    }
/*
빈 메시지 1
빈 메시지 2
빈 메시지 1
빈 메시지 3
21:56:22.220 [Thread-0] DEBUG org.springframework.context.support.FileSystemXmlApplicationContext - Closing org.springframework.context.support.FileSystemXmlApplicationContext@6ad82709, started on Tue Sep 17 21:56:21 KST 2019
 */
}
