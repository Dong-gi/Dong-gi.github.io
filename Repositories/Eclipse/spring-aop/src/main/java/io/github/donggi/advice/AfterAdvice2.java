package io.github.donggi.advice;

public class AfterAdvice2 {

    public void after1() {
        System.out.println("after(void)");
    }
    
    public void after2(Throwable t) {
        System.out.println("after(Thrwoable) : " + t.getMessage());
    }
}
