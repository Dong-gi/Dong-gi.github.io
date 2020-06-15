package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello6Test {

    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans13.xml")) {
            log.info(context.getBean("hello6"));
        }
    }
/*
19:32:22.325 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'org.springframework.context.annotation.internalConfigurationAnnotationProcessor'
19:32:22.366 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'org.springframework.context.event.internalEventListenerProcessor'
19:32:22.368 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'org.springframework.context.event.internalEventListenerFactory'
19:32:22.369 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'org.springframework.context.annotation.internalAutowiredAnnotationProcessor'
19:32:22.370 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'org.springframework.context.annotation.internalCommonAnnotationProcessor'
19:32:22.372 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'hello6'
19:32:22.386 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'java.lang.String#0'
19:32:22.406 [main] INFO org.springframework.context.support.PostProcessorRegistrationDelegate$BeanPostProcessorChecker - Bean 'java.lang.String#0' of type [java.lang.String] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying)
기본 메시지
 */
}
