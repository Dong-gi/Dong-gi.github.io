package io.github.donggi.advice;

import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import io.github.donggi.service.PrintService;

public class AfterAdviceTest {
    @Test
    public void test() {
        try (var context = new ClassPathXmlApplicationContext("beans/Beans3.xml")) {
            ((PrintService) context.getBean("printService1")).print("Hello World");
        }
    }
/*
Hello World
after with no args
after with String : Hello World
*/
}
