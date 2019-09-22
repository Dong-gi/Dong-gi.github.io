package io.github.donggi.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class AfterAspect3 {

    @After("AroundAspect.aroundTarget()")
    public void after1() {
        System.out.println("after(void)");
    }
    
    @After("AroundAspect.aroundTarget()")
    public void after2(JoinPoint joinPoint) {
        System.out.println("after(JoinPoint) : " + joinPoint.toShortString());
        System.out.println("this : " + joinPoint.getThis());
    }
    
}
