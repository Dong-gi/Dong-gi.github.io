package io.github.donggi.bean;

import lombok.Data;

@Data
public class Hello23 implements AutoCloseable {
    private String message;

    @Override
    public void close() {
        System.out.println("Hello3 Bean Destroyed...");
    }
}
