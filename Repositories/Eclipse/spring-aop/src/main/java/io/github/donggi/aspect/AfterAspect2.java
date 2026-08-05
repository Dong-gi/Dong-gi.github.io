package io.github.donggi.aspect;

import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class AfterAspect2 {
    @Pointcut("execution(public * io.github.donggi..*(..))")
    private void afterTarget() {}

    @AfterThrowing("afterTarget()")
    public void after1() {
        System.out.println("after with no args");
    }
    @AfterThrowing(pointcut = "afterTarget()", throwing = "t")
    public void after2(Throwable t) {
        System.out.println("after with Thrwoable : " + t.getMessage());
    }
}
