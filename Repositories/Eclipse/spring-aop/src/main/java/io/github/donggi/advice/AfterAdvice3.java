package io.github.donggi.advice;

import org.aspectj.lang.JoinPoint;

public class AfterAdvice3 {
    public void after1() {
        System.out.println("after with no args");
    }
    public void after2(JoinPoint joinPoint) {
        System.out.println("after with JoinPoint : " + joinPoint.toShortString());
        System.out.println("this : " + joinPoint.getThis());
    }
}
