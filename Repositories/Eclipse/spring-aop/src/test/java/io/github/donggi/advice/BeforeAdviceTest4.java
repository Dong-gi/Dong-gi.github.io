package io.github.donggi.advice;

import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import io.github.donggi.service.PrintService;

public class BeforeAdviceTest4 {
    @Test
    public void test() {
        try (var context = new ClassPathXmlApplicationContext("beans/Beans12.xml")) {
            ((PrintService) context.getBean("printService1")).print("Hello World");
        }
    }
/*
before(void) : Hello World
before(JoinPoint) : execution(PrintService.print(..))
param : Hello World
Hello World
 */
}
