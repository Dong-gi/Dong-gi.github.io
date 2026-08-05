package io.github.donggi.example.entity;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.ApplicationScope;

import lombok.Data;

@Data
@Component
@ApplicationScope
public class PerApp {
    private static int count = 0;
    private int num = PerApp.count++; 
}
