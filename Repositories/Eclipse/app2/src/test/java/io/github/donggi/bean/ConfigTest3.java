package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.support.AbstractApplicationContext;

@Configuration
@ImportResource("file:src/main/resource/Beans22.xml")
public class ConfigTest3 {

    @Test
    public void test() {
        ApplicationContext context = new AnnotationConfigApplicationContext(ConfigTest3.class);
        ((AbstractApplicationContext) context).registerShutdownHook();
        System.out.println((Hello1)context.getBean("hello1"));
        System.out.println((Hello1)context.getBean("hello1-2"));
        System.out.println((Hello6)context.getBean("hello6"));
    }
/*
Hello1(message=Hello1 메시지)
Hello1(message=기본 메시지)
Hello6(message=기본 메시지)
 */
}