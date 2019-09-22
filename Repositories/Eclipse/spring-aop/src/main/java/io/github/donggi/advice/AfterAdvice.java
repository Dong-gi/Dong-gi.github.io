package io.github.donggi.advice;

public class AfterAdvice {

    public void after1() {
        System.out.println("after(void)");
    }
    
    public void after2(String message) {
        System.out.println("after(String) : " + message);
    }
}
