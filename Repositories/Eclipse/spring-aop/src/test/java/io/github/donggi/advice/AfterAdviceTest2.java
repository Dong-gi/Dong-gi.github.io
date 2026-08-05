package io.github.donggi.advice;

import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import io.github.donggi.service.PrintService;

public class AfterAdviceTest2 {
    @Test
    public void test() {
        try (var context = new ClassPathXmlApplicationContext("beans/Beans4.xml")) {
            ((PrintService) context.getBean("printService2")).print("Hello World");
        }
    }
/*
after with no args
after with Thrwoable : 그냥 던져봤어
*/
}
