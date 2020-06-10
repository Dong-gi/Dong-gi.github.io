package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello15Test2 {
    @Test
    public void test() {
        ApplicationContext context = new FileSystemXmlApplicationContext("src/main/resource/Beans28.xml");
        log.info(((Hello15) context.getBean("hello15")).getHello16());
        log.info(((Hello16) context.getBean("hello16")).getMessage());
    }
/*
15:00:09.166 [main] DEBUG org.jboss.logging - Logging Provider: org.jboss.logging.Log4j2LoggerProvider
15:00:09.364 [main] DEBUG org.springframework.context.support.FileSystemXmlApplicationContext - Refreshing org.springframework.context.support.FileSystemXmlApplicationContext@67f639d3
15:00:09.876 [main] DEBUG org.springframework.beans.factory.xml.XmlBeanDefinitionReader - Loaded 1 bean definitions from file [C:\tomcat\webapps\github\Repositories\Eclipse\app2\src\main\resource\Beans27.xml]
15:00:10.066 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'io.github.donggi.bean.Hello17#0'
15:00:10.385 [main] INFO io.github.donggi.bean.Hello17Test3 - Hello Anonymous 17
 */
}
