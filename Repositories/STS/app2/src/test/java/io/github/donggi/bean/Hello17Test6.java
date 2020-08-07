package io.github.donggi.bean;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello17Test6 {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans44.xml")) {
            log.info(context.getBean("h1"));
            log.info(context.getBean("h2"));
        }
    }
/*
13:00:27.465 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'org.springframework.context.support.PropertySourcesPlaceholderConfigurer#0'
13:00:27.622 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'props1'
13:00:27.648 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'org.springframework.context.support.PropertySourcesPlaceholderConfigurer#1'
13:00:27.649 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'props2'
13:00:27.651 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'org.springframework.beans.factory.config.PropertyOverrideConfigurer#0'
13:00:27.659 [main] DEBUG org.springframework.core.env.PropertySourcesPropertyResolver - Found key 'hello.class' in PropertySource 'localProperties' with value of type String
13:00:27.659 [main] DEBUG org.springframework.core.env.PropertySourcesPropertyResolver - Found key 'hello.message' in PropertySource 'localProperties' with value of type String
13:00:27.659 [main] DEBUG org.springframework.core.env.PropertySourcesPropertyResolver - Found key 'hello17.message' in PropertySource 'localProperties' with value of type String
13:00:27.661 [main] DEBUG org.springframework.core.env.PropertySourcesPropertyResolver - Found key 'hello17.class' in PropertySource 'localProperties' with value of type String
13:00:27.662 [main] DEBUG org.springframework.beans.factory.config.PropertyOverrideConfigurer - Property 'h2.message' set to value [Overried Message 17]
13:00:27.663 [main] DEBUG org.springframework.beans.factory.config.PropertyOverrideConfigurer - Property 'h1.message' set to value [Overried Message 1]
13:00:27.672 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'h1'
13:00:27.679 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'h2'
13:00:27.748 [main] INFO io.github.donggi.bean.Hello17Test6 - Hello1(message=Overried Message 1)
13:00:27.748 [main] INFO io.github.donggi.bean.Hello17Test6 - Hello17(message=Overried Message 17)
*/
}
