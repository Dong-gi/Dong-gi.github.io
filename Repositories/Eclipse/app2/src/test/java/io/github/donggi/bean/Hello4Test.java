package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello4Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans4.xml") {
            @Override
            protected void onClose() {
                super.onClose();
                log.info("Context closed...");
            }
        })
        {
            ((AbstractApplicationContext) context).registerShutdownHook();
            System.exit(-1);
        }
    }
/*
16:10:38.594 [main] DEBUG org.jboss.logging - Logging Provider: org.jboss.logging.Log4j2LoggerProvider
16:10:38.679 [main] DEBUG io.github.donggi.bean.Hello4Test$1 - Refreshing io.github.donggi.bean.Hello4Test$1@47e2e487
16:10:38.970 [main] DEBUG org.springframework.beans.factory.xml.XmlBeanDefinitionReader - Loaded 1 bean definitions from file [C:\tomcat\webapps\github\Repositories\Eclipse\app2\src\main\resource\Beans4.xml]
16:10:39.021 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'io.github.donggi.bean.Hello4#0'
16:10:39.168 [Thread-0] DEBUG io.github.donggi.bean.Hello4Test$1 - Closing io.github.donggi.bean.Hello4Test$1@47e2e487, started on Thu Jun 11 16:10:38 KST 2020
16:10:39.172 [Thread-0] INFO io.github.donggi.bean.Hello4Test - Context closed...
*/
}
