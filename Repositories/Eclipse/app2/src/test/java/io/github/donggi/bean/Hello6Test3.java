package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class Hello6Test3 {

    public void test1() {
        ApplicationContext context = new FileSystemXmlApplicationContext("src/main/resource/Beans15.xml");
        ((AbstractApplicationContext) context).registerShutdownHook();
        System.out.println(((Hello6) context.getBean("hello6")).getMessage());
    }
/*
No qualifying bean of type 'java.lang.String' available: expected at least 1 bean which qualifies as autowire candidate.
Dependency annotations: {@org.springframework.beans.factory.annotation.Autowired(required=true)}
 */

    @Test
    public void test2() {
        ApplicationContext context = new FileSystemXmlApplicationContext("src/main/resource/Beans16.xml");
        ((AbstractApplicationContext) context).registerShutdownHook();
        System.out.println(((Hello7) context.getBean("hello7")).getMessage());
    }
/*
19:40:40.176 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'hello7'
null
 */
}
