package io.github.donggi.bean;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class Hello4Test {

    @Test
    public void test() {
        System.out.println(new File("").getAbsolutePath());
        ApplicationContext context = new FileSystemXmlApplicationContext("src/main/resource/Beans4.xml");
        ((AbstractApplicationContext) context).registerShutdownHook();
        var message = ((Hello4) context.getBean(Hello4.class.getCanonicalName() + "#0")).getMessage();
        System.out.println(message);
        assertTrue(message != null);
    }
/*
20:44:46.049 [main] DEBUG org.springframework.context.support.FileSystemXmlApplicationContext - Refreshing org.springframework.context.support.FileSystemXmlApplicationContext@6ad82709
20:44:46.262 [main] DEBUG org.springframework.beans.factory.xml.XmlBeanDefinitionReader - Loaded 1 bean definitions from file [D:\apache-tomcat-9.0.17\webapps\github\Repositories\Eclipse\app2\src\main\resource\Beans4.xml]
20:44:46.289 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'io.github.donggi.bean.Hello4#0'
Hell4 - 메시지
20:44:46.370 [Thread-0] DEBUG org.springframework.context.support.FileSystemXmlApplicationContext - Closing org.springframework.context.support.FileSystemXmlApplicationContext@6ad82709, started on Tue Sep 17 20:44:46 KST 2019
 */
}
