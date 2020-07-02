package io.github.donggi.aspect;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class AfterAspect {
    @Pointcut("execution(public * io.github.donggi..*(..))")
    private void afterTarget() {}

    @AfterReturning("afterTarget()")
    public void after1() {
        System.out.println("after with no args");
    }
    @AfterReturning(pointcut = "afterTarget()", returning = "message")
    public void after2(String message) {
        System.out.println("after with String : " + message);
    }
}
