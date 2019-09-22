package io.github.donggi.advice;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import io.github.donggi.service.PrintService;

public class AfterAdviceTest6 {

    @Test
    public void test() {
        ApplicationContext context = new ClassPathXmlApplicationContext("beans/Beans10.xml");
        ((AbstractApplicationContext) context).registerShutdownHook();
        ((PrintService) context.getBean("printService1")).print("Hello World");
        ((PrintService) context.getBean("printService2")).print("Hello World");
    }
/*
Hello World
after(void)
after(JoinPoint) : execution(PrintService.print(..))
this : io.github.donggi.service.impl.PrintServiceImpl@6d7fc27
after(void)
after(JoinPoint) : execution(PrintService.print(..))
this : io.github.donggi.service.impl.PrintServiceImpl2@2a3888c1
 */
}
