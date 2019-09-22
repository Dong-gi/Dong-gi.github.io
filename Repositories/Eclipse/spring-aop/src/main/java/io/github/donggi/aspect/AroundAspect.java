package io.github.donggi.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class AroundAspect {

    @Pointcut("execution(public * io.github.donggi..*(..))")
    public void aroundTarget() {}
    
    @Around("aroundTarget()")
    public Object timer(ProceedingJoinPoint joinPoint) throws Throwable {
        var start = System.currentTimeMillis();
        System.out.println(this);
        var signature = joinPoint.getSignature().toShortString();
        try {
            return joinPoint.proceed();
        } catch(RuntimeException e) {
            System.out.println(e.getMessage());
            return null;
        } finally {
            System.out.printf("%s 실행 시간 : %d\n", signature, System.currentTimeMillis() - start);
        }
    }
    
}
