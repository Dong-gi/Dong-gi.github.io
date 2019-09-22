package io.github.donggi.advice;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import io.github.donggi.service.PrintService;

public class TimerAdviceTest {

    @Test
    public void test() {
        ApplicationContext context = new ClassPathXmlApplicationContext("beans/Beans1.xml");
        ((AbstractApplicationContext) context).registerShutdownHook();
        System.out.println(context.getBean("timerAdvice"));
        ((PrintService) context.getBean("printService1")).print("Hello World");
        ((PrintService) context.getBean("printService2")).print("Hello World");
    }
/*
io.github.donggi.advice.TimerAdvice@5316e95f
io.github.donggi.advice.TimerAdvice@5316e95f
Hello World
PrintService.print(..) 실행 시간 : 101
io.github.donggi.advice.TimerAdvice@5316e95f
그냥 던져봤어
PrintService.print(..) 실행 시간 : 3
 */
}
