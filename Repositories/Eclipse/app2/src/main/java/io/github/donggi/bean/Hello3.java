package io.github.donggi.bean;

import lombok.Data;

@Data
public class Hello3 {
    private String message;
    
    public void init() {
        System.out.println("Hello3 Bean Initialized...");
    }

    public void destroy() {
        System.out.println("Hello3 Bean Destroyed...");
    }
}
