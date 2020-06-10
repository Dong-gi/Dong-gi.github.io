package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello4Test {
    @Test
    public void test() {
        var context = new FileSystemXmlApplicationContext("src/main/resource/Beans4.xml") {
            @Override
            protected void onClose() {
                super.onClose();
                log.info("Context closed...");
            }
        };
        ((AbstractApplicationContext) context).registerShutdownHook();
        log.info(context.getBean(Hello4.class.getCanonicalName() + "#0"));
        System.exit(-1);
    }
/*
11:29:34.983 [main] DEBUG org.jboss.logging - Logging Provider: org.jboss.logging.Log4j2LoggerProvider
11:29:35.060 [main] DEBUG io.github.donggi.bean.Hello4Test$1 - Refreshing io.github.donggi.bean.Hello4Test$1@69379752
11:29:35.370 [main] DEBUG org.springframework.beans.factory.xml.XmlBeanDefinitionReader - Loaded 1 bean definitions from file [C:\tomcat\webapps\github\Repositories\Eclipse\app2\src\main\resource\Beans4.xml]
11:29:35.403 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'io.github.donggi.bean.Hello4#0'
11:29:35.473 [main] INFO io.github.donggi.bean.Hello4Test - Hell4 - 메시지
11:29:35.485 [Thread-0] DEBUG io.github.donggi.bean.Hello4Test$1 - Closing io.github.donggi.bean.Hello4Test$1@69379752, started on Mon Jun 08 11:29:35 KST 2020
11:29:35.491 [Thread-0] INFO io.github.donggi.bean.Hello4Test - Context closed...
 */
}
