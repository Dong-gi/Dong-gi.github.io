package io.github.donggi.advice;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import io.github.donggi.service.PrintService;

public class AfterAdviceTest {

    @Test
    public void test() {
        ApplicationContext context = new ClassPathXmlApplicationContext("beans/Beans3.xml");
        ((AbstractApplicationContext) context).registerShutdownHook();
        ((PrintService) context.getBean("printService1")).print("Hello World");
    }
/*
Hello World
after(void)
after(String) : Hello World
 */
}
