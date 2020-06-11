package io.github.donggi.example.entity;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import lombok.Data;

@Data
@Component
@RequestScope
public class PerRequest {
    private static int count = 0;
    private int num = PerRequest.count++; 
}
