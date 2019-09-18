package io.github.donggi.bean;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class Hello1Test {

    @Test
    public void test() {
        System.out.println(new File("").getAbsolutePath());
        ApplicationContext context = new FileSystemXmlApplicationContext("src/main/resource/Beans1.xml");
        ((AbstractApplicationContext) context).registerShutdownHook();
        var message = ((Hello1) context.getBean("hello1")).getMessage();
        System.out.println(message);
        assertTrue(message != null);
    }
/*
21:40:42.021 [main] DEBUG org.springframework.context.support.FileSystemXmlApplicationContext - Refreshing org.springframework.context.support.FileSystemXmlApplicationContext@7770f470
21:40:42.248 [main] DEBUG org.springframework.beans.factory.xml.XmlBeanDefinitionReader - Loaded 1 bean definitions from file [D:\apache-tomcat-9.0.17\webapps\github\Repositories\Eclipse\app2\src\main\resource\Beans1.xml]
21:40:42.280 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'hello1'
Hello1 - 메시지
21:40:42.363 [Thread-0] DEBUG org.springframework.context.support.FileSystemXmlApplicationContext - Closing org.springframework.context.support.FileSystemXmlApplicationContext@7770f470, started on Mon Sep 16 21:40:42 KST 2019
 */
}
