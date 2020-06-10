package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello4Test5 {

    @Test
    public void test() {
        ApplicationContext context = new FileSystemXmlApplicationContext("src/main/resource/Beans24.xml");
        log.info(context.getBean("hello4", Hello4.class).getMessage());
    }
/*
11:15:37.797 [main] DEBUG org.springframework.context.support.FileSystemXmlApplicationContext - Refreshing org.springframework.context.support.FileSystemXmlApplicationContext@5cb9f472
11:15:38.225 [main] DEBUG org.springframework.beans.factory.xml.XmlBeanDefinitionReader - Loaded 1 bean definitions from file [C:\tomcat\webapps\github\Repositories\Eclipse\app2\src\main\resource\Beans24.xml]
11:15:38.272 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'hello4'
Hell4 - 메시지
 */
}
