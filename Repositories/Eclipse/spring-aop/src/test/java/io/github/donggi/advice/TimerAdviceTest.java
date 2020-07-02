package io.github.donggi.advice;

import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import io.github.donggi.service.PrintService;

public class TimerAdviceTest {
    @Test
    public void test() {
        try (var context = new ClassPathXmlApplicationContext("beans/Beans1.xml")) {
            System.out.println(context.getBean("timerAdvice"));
            ((PrintService) context.getBean("printService1")).print("Hello World");
            ((PrintService) context.getBean("printService2")).print("Hello World");
        }
    }
/*
io.github.donggi.advice.TimerAdvice@662f5666
io.github.donggi.advice.TimerAdvice@662f5666
Hello World
PrintService.print(..) 실행 시간 : 127
io.github.donggi.advice.TimerAdvice@662f5666
그냥 던져봤어
PrintService.print(..) 실행 시간 : 4
*/
}
