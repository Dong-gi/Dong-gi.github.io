package io.github.donggi.advice;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import io.github.donggi.service.PrintService;

public class TimerAdviceTest2 {

    @Test
    public void test() {
        ApplicationContext context = new ClassPathXmlApplicationContext("beans/Beans6.xml");
        ((AbstractApplicationContext) context).registerShutdownHook();
        ((PrintService) context.getBean("printService1")).print("Hello World");
        ((PrintService) context.getBean("printService2")).print("Hello World");
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
