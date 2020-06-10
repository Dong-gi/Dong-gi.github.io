package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello4Test2 {

    @Test
    public void test() {
        ApplicationContext context = new FileSystemXmlApplicationContext("src/main/resource/Beans6.xml");
        ((AbstractApplicationContext) context).registerShutdownHook();
        log.info(((Hello4) context.getBean(Hello4.class.getCanonicalName() + "#0")).getMessage());
    }
/*
21:49:56.660 [main] DEBUG org.springframework.context.support.FileSystemXmlApplicationContext - Refreshing org.springframework.context.support.FileSystemXmlApplicationContext@7770f470
21:49:56.888 [main] DEBUG org.springframework.beans.factory.xml.XmlBeanDefinitionReader - Loaded 1 bean definitions from file [D:\apache-tomcat-9.0.17\webapps\github\Repositories\Eclipse\app2\src\main\resource\Beans6.xml]
21:49:56.919 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'io.github.donggi.bean.Hello4#0'
Hell4 - 메시지
21:49:57.002 [Thread-0] DEBUG org.springframework.context.support.FileSystemXmlApplicationContext - Closing org.springframework.context.support.FileSystemXmlApplicationContext@7770f470, started on Tue Sep 17 21:49:56 KST 2019
 */
}
