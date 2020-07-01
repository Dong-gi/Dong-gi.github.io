package io.github.donggi.advice;

import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import io.github.donggi.service.PrintService;

public class TimerAdviceTest2 {
    @Test
    public void test() {
        try (var context = new ClassPathXmlApplicationContext("beans/Beans6.xml")) {
            ((PrintService) context.getBean("printService1")).print("Hello World");
            ((PrintService) context.getBean("printService2")).print("Hello World");
        }
    }
/*
io.github.donggi.aspect.AroundAspect@4264b240
Hello World
PrintService.print(..) 실행 시간 : 102
io.github.donggi.aspect.AroundAspect@4264b240
그냥 던져봤어
PrintService.print(..) 실행 시간 : 2
 */
}
