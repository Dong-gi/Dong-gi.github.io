package io.github.donggi.anno;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Component;

@Component
public class Anno21 {
    @PostConstruct
    public void init() {
        System.out.println("Anno21 Bean Initialized...");
    }
    @PreDestroy
    public void destroy() {
        System.out.println("Anno21 Bean Destroyed...");
    }
}