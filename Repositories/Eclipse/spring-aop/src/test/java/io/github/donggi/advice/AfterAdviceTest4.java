package io.github.donggi.advice;

import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import io.github.donggi.service.PrintService;

public class AfterAdviceTest4 {
    @Test
    public void test() {
        try (var context = new ClassPathXmlApplicationContext("beans/Beans8.xml")) {
            ((PrintService) context.getBean("printService1")).print("Hello World");
        }
    }
/*
Hello World
after(void)
after(String) : Hello World
 */
}
