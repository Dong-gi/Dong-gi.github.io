package io.github.donggi.advice;

import org.aspectj.lang.JoinPoint;

public class BeforeAdvice2 {

    public void before1(String message) {
        System.out.println("before(void) : " + message);
    }
    
    public void before2(JoinPoint joinPoint, String message) {
        System.out.println("before(JoinPoint) : " + joinPoint.toShortString());
        System.out.println("param : " + message);
    }
}
