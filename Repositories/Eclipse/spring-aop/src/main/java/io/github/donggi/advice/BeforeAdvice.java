package io.github.donggi.advice;

import java.util.Arrays;

import org.aspectj.lang.JoinPoint;

public class BeforeAdvice {
    public void before1() {
        System.out.println("before with no args");
    }
    public void before2(JoinPoint joinPoint) {
        System.out.println("before with JoinPoint : " + joinPoint.toShortString());
        System.out.println("param : " + Arrays.deepToString(joinPoint.getArgs()));
    }
}
