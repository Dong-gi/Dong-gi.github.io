package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class Hello3Test {

    @Test
    public void test() {
        ApplicationContext context = new FileSystemXmlApplicationContext("src/main/resource/Beans3.xml");
        ((AbstractApplicationContext) context).registerShutdownHook();
        System.out.println(((Hello3) context.getBean("hello3")).getMessage());
    }
/*
21:43:49.630 [main] DEBUG org.springframework.context.support.FileSystemXmlApplicationContext - Refreshing org.springframework.context.support.FileSystemXmlApplicationContext@7770f470
21:43:49.848 [main] DEBUG org.springframework.beans.factory.xml.XmlBeanDefinitionReader - Loaded 1 bean definitions from file [D:\apache-tomcat-9.0.17\webapps\github\Repositories\Eclipse\app2\src\main\resource\Beans3.xml]
21:43:49.885 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'hello3'
Hello3 Bean Initialized...
Hello3 - 메시지
21:43:49.972 [Thread-0] DEBUG org.springframework.context.support.FileSystemXmlApplicationContext - Closing org.springframework.context.support.FileSystemXmlApplicationContext@7770f470, started on Mon Sep 16 21:43:49 KST 2019
Hello3 Bean Destroyed...
 */
}
