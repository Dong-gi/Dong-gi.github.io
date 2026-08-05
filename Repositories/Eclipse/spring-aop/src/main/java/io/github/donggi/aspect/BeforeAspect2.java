package io.github.donggi.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class BeforeAspect2 {
    @Before("args(message)")
    public void before1(String message) {
        System.out.println("before with no args : " + message);
    }
    @Before("args(message)")
    public void before2(JoinPoint joinPoint, String message) {
        System.out.println("before with JoinPoint : " + joinPoint.toShortString());
        System.out.println("param : " + message);
    }
}
