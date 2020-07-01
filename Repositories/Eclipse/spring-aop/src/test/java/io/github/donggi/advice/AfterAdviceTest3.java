package io.github.donggi.advice;

import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import io.github.donggi.service.PrintService;

public class AfterAdviceTest3 {
    @Test
    public void test() {
        try (var context = new ClassPathXmlApplicationContext("beans/Beans5.xml")) {
            ((PrintService) context.getBean("printService1")).print("Hello World");
            ((PrintService) context.getBean("printService2")).print("Hello World");
        }
    }
/*
Hello World
after(void)
after(JoinPoint) : execution(PrintService.print(..))
this : io.github.donggi.service.impl.PrintServiceImpl@5357c287
after(void)
after(JoinPoint) : execution(PrintService.print(..))
this : io.github.donggi.service.impl.PrintServiceImpl2@5b04476e
 */
}
