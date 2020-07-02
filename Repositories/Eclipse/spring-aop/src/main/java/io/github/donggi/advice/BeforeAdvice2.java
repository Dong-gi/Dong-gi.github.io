package io.github.donggi.advice;

import org.aspectj.lang.JoinPoint;

public class BeforeAdvice2 {
    public void before1(String message) {
        System.out.println("before with no args : " + message);
    }
    public void before2(JoinPoint joinPoint, String message) {
        System.out.println("before with JoinPoint : " + joinPoint.toShortString());
        System.out.println("param : " + message);
    }
}
