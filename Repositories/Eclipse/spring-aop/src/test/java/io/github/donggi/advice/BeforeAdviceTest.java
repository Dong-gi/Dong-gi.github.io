package io.github.donggi.advice;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import io.github.donggi.service.PrintService;

public class BeforeAdviceTest {

    @Test
    public void test() {
        ApplicationContext context = new ClassPathXmlApplicationContext("beans/Beans2.xml");
        ((AbstractApplicationContext) context).registerShutdownHook();
        ((PrintService) context.getBean("printService1")).print("Hello World");
    }
/*
before(void)
before(JoinPoint) : execution(PrintService.print(..))
param : [Hello World]
Hello World
 */
}
