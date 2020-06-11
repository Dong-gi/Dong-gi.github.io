package io.github.donggi.example.entity;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import lombok.Data;

@Data
@Component
@SessionScope
public class PerSession {
    private static int count = 0;
    private int num = PerSession.count++; 
}
