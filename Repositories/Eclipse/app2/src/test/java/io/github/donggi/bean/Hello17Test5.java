package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello17Test5 {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans43.xml")) {
            log.info(context.getBean("h1"));
            log.info(context.getBean("h2"));
        }
    }
/*
11:24:15.827 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'org.springframework.context.support.PropertySourcesPlaceholderConfigurer#0'
11:24:16.002 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'props1'
11:24:16.024 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'org.springframework.context.support.PropertySourcesPlaceholderConfigurer#1'
11:24:16.024 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'props2'
11:24:16.029 [main] DEBUG org.springframework.core.env.PropertySourcesPropertyResolver - Found key 'hello.class' in PropertySource 'localProperties' with value of type String
11:24:16.029 [main] DEBUG org.springframework.core.env.PropertySourcesPropertyResolver - Found key 'hello.message' in PropertySource 'localProperties' with value of type String
11:24:16.029 [main] DEBUG org.springframework.core.env.PropertySourcesPropertyResolver - Found key 'hello17.message' in PropertySource 'localProperties' with value of type String
11:24:16.031 [main] DEBUG org.springframework.core.env.PropertySourcesPropertyResolver - Found key 'hello17.class' in PropertySource 'localProperties' with value of type String
11:24:16.042 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'h1'
11:24:16.046 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'h2'
11:24:16.088 [main] INFO io.github.donggi.bean.Hello17Test5 - Hello1(message=Hello World 1)
11:24:16.088 [main] INFO io.github.donggi.bean.Hello17Test5 - Hello17(message=Hello World 17)
*/
}
