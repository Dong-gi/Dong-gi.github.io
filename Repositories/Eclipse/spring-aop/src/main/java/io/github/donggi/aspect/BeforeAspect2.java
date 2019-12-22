package io.github.donggi.aspect;

import java.util.Arrays;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class BeforeAspect2 {

    @Before("args(message)")
    public void before1(String message) {
        System.out.println("before(void) : " + message);
    }

    @Before("args(message)")
    public void before2(JoinPoint joinPoint, String message) {
        System.out.println("before(JoinPoint) : " + joinPoint.toShortString());
        System.out.println("param : " + message);
    }

}
