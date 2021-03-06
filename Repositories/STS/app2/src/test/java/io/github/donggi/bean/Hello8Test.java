package io.github.donggi.bean;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello8Test {

    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans17.xml")) {
            ((AbstractApplicationContext) context).registerShutdownHook();
            log.info(((Hello8) context.getBean("hello8")).getMessage());
            log.info(((Hello9) context.getBean("hello9")).getMessage());
        }
    }
    /*
20:21:20.875 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'hello8'
20:21:20.891 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'ko'
20:21:20.909 [main] INFO org.springframework.context.support.PostProcessorRegistrationDelegate$BeanPostProcessorChecker - Bean 'ko' of type [java.lang.String] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying)
20:21:20.910 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'hello9'
20:21:20.914 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'en'
20:21:20.916 [main] INFO org.springframework.context.support.PostProcessorRegistrationDelegate$BeanPostProcessorChecker - Bean 'en' of type [java.lang.String] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying)
메시지
message
     */
}
