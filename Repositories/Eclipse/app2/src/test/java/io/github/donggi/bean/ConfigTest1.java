package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.AbstractApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@Configuration
@JBossLog
public class ConfigTest1 {

    @Bean
    public String message() {
        return "기본 메시지";
    }

    @Bean
    public Hello1 hello1() {
        var hello1 = new Hello1();
        hello1.setMessage("Hello1 메시지");
        return hello1;
    }

    @Bean(name = "hello1-2")
    public Hello1 hello1_2() {
        var hello1 = new Hello1();
        hello1.setMessage(message());
        return hello1;
    }

    @Bean
    public Hello6 hello6() {
        return new Hello6();
    }

    @Test
    public void test() {
        ApplicationContext context = new AnnotationConfigApplicationContext(ConfigTest1.class);
        ((AbstractApplicationContext) context).registerShutdownHook();
        log.info((Hello1)context.getBean("hello1"));
        log.info((Hello1)context.getBean("hello1-2"));
        log.info((Hello6)context.getBean("hello6"));
    }
/*
Hello1(message=Hello1 메시지)
Hello1(message=기본 메시지)
Hello6(message=기본 메시지)
 */
}
