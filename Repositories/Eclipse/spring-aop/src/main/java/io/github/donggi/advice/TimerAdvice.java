package io.github.donggi.advice;

import org.aspectj.lang.ProceedingJoinPoint;

public class TimerAdvice {
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
