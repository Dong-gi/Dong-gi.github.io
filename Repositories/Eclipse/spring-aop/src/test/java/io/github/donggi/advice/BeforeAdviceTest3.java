package io.github.donggi.advice;

import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import io.github.donggi.service.PrintService;

public class BeforeAdviceTest3 {

    @Test
    public void test() {
        try (var context = new ClassPathXmlApplicationContext("beans/Beans11.xml")) {
            ((PrintService) context.getBean("printService1")).print("Hello World");
        }
    }
/*
before(void)
before(JoinPoint) : execution(PrintService.print(..))
param : [Hello World]
Hello World
 */
}
