package io.github.donggi.aspect;

import java.util.Arrays;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class BeforeAspect {
    @Before("AroundAspect.aroundTarget()")
    public void before1() {
        System.out.println("before with no args");
    }
    @Before("execution(public * io.github.donggi..*(..))")
    public void before2(JoinPoint joinPoint) {
        System.out.println("before with JoinPoint : " + joinPoint.toShortString());
        System.out.println("param : " + Arrays.deepToString(joinPoint.getArgs()));
    }
}
