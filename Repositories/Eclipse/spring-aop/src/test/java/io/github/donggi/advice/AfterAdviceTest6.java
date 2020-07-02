package io.github.donggi.advice;

import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import io.github.donggi.service.PrintService;

public class AfterAdviceTest6 {
    @Test
    public void test() {
        try (var context = new ClassPathXmlApplicationContext("beans/Beans10.xml")) {
            ((PrintService) context.getBean("printService1")).print("Hello World");
            ((PrintService) context.getBean("printService2")).print("Hello World");
        }
    }
/*
Hello World
after with no args
after with JoinPoint : execution(PrintService.print(..))
this : io.github.donggi.service.impl.PrintServiceImpl@12a94400
after with no args
after with JoinPoint : execution(PrintService.print(..))
this : io.github.donggi.service.impl.PrintServiceImpl2@2a225dd7
*/
}
