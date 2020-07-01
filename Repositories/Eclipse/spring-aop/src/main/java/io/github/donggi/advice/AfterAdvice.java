package io.github.donggi.advice;

public class AfterAdvice {

    public void after1() {
        System.out.println("after with no args");
    }

    public void after2(String message) {
        System.out.println("after with String : " + message);
    }
}
