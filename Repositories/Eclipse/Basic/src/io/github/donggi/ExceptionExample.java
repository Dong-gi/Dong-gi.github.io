package io.github.donggi;

import java.io.IOException;

public class ExceptionExample {
    public static void main(String[] args) throws Exception {

        try {
            throw new IOException("new exception");
        } catch (IOException | IllegalArgumentException e) {
            // Java 1.7부터 | 연산자로 여러 예외 동시 처리 가능
            System.out.println(e.getMessage());
        }

        // 사용자 정의 예외 클래스 이름에 접미사로 Exception을 붙인다.
        // 자식은 부모보다 많은 종류의 예외를 발생시킬 수 없다.
        try {
            throw new IOException("예외 발생");
        } catch (IOException ioe) {
            try {
                throw new Exception("추가 예외");
            } catch (Exception e) {
                ioe.addSuppressed(e); // 추가 예외 억제
                ioe.initCause(new Exception("예외 원인은 나다"));
                for (var throwable : ioe.getSuppressed()) {
                    System.out.println("Suppressed : " + throwable);
                }
                Thread.sleep(1000);
            }
            throw ioe; // 이런 식으로 호출자에게 넘겨 추가 작업을 진행할 수 있다.
        } finally {
        }
        // finally가 catch 뒤에 나오면 예외를 처리한 뒤에 실행.
        // finally가 catch 전에 나오면 예외를 처리하기 전에 실행.
    }

}