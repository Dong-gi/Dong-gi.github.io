package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class Hello15Test {

    public void test1() throws Exception {
        ApplicationContext context = new FileSystemXmlApplicationContext("src/main/resource/Beans20.xml");
        ((AbstractApplicationContext) context).registerShutdownHook();
        System.out.println(((Hello15) context.getBean("hello15")).getHello16().getMessage());
        Thread.sleep(1000);
        System.out.println(((Hello15) context.getBean("hello15")).getHello16().getMessage());
        Thread.sleep(1000);
        System.out.println(((Hello15) context.getBean("hello15")).getHello16().getMessage());
    }
/*
Fri Sep 20 06:56:43 KST 2019
Fri Sep 20 06:56:43 KST 2019
Fri Sep 20 06:56:43 KST 2019
 */
    @Test
    public void test2() throws Exception {
        ApplicationContext context = new FileSystemXmlApplicationContext("src/main/resource/Beans21.xml");
        ((AbstractApplicationContext) context).registerShutdownHook();
        System.out.println(((Hello15) context.getBean("hello15")).getHello16().getMessage());
        Thread.sleep(1000);
        System.out.println(((Hello15) context.getBean("hello15")).getHello16().getMessage());
        Thread.sleep(1000);
        System.out.println(((Hello15) context.getBean("hello15")).getHello16().getMessage());
    }
/*
Fri Sep 20 06:59:20 KST 2019
Fri Sep 20 06:59:21 KST 2019
Fri Sep 20 06:59:22 KST 2019
 */
}
