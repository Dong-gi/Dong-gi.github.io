package io.github.donggi.advice;

import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import io.github.donggi.service.PrintService;

public class AfterAdviceTest5 {

    @Test
    public void test() {
        try (var context = new ClassPathXmlApplicationContext("beans/Beans9.xml")) {
            ((PrintService) context.getBean("printService2")).print("Hello World");
        }
    }
/*
after(void)
after(Thrwoable) : 그냥 던져봤어
 */
}
