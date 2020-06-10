package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello2Test {

    @Test
    public void test() {
        ApplicationContext context = new FileSystemXmlApplicationContext("src/main/resource/Beans2.xml");
        ((AbstractApplicationContext) context).registerShutdownHook();
        log.info(((Hello2) context.getBean("hello2")).getMessage());
    }
/*
21:39:54.617 [main] DEBUG org.springframework.context.support.FileSystemXmlApplicationContext - Refreshing org.springframework.context.support.FileSystemXmlApplicationContext@6ad82709
21:39:54.844 [main] DEBUG org.springframework.beans.factory.xml.XmlBeanDefinitionReader - Loaded 1 bean definitions from file [D:\apache-tomcat-9.0.17\webapps\github\Repositories\Eclipse\app2\src\main\resource\Beans2.xml]
21:39:54.877 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'hello2'
Hello2 Bean Initialized...
Hello2 - 메시지
21:39:54.967 [Thread-0] DEBUG org.springframework.context.support.FileSystemXmlApplicationContext - Closing org.springframework.context.support.FileSystemXmlApplicationContext@6ad82709, started on Mon Sep 16 21:39:54 KST 2019
Hello2 Bean Destroyed...
 */
}
