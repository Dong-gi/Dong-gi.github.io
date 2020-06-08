package io.github.donggi.bean;

import org.jboss.logging.Logger;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class Hello4Test6 {

    private final Logger LOG = Logger.getLogger(Hello4Test6.class);
    
    @Test
    public void test() {
        ApplicationContext context = new FileSystemXmlApplicationContext("src/main/resource/Beans4.xml");
        LOG.info(((Hello4) context.getBean(Hello4.class.getCanonicalName() + "#0")).getMessage());
    }
/*
11:30:14.707 [main] DEBUG org.jboss.logging - Logging Provider: org.jboss.logging.Log4j2LoggerProvider
11:30:14.770 [main] DEBUG org.springframework.context.support.FileSystemXmlApplicationContext - Refreshing org.springframework.context.support.FileSystemXmlApplicationContext@382db087
11:30:14.961 [main] DEBUG org.springframework.beans.factory.xml.XmlBeanDefinitionReader - Loaded 1 bean definitions from file [C:\tomcat\webapps\github\Repositories\Eclipse\app2\src\main\resource\Beans4.xml]
11:30:14.987 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'io.github.donggi.bean.Hello4#0'
11:30:15.055 [main] INFO io.github.donggi.bean.Hello4Test6 - Hell4 - 메시지
 */
}
