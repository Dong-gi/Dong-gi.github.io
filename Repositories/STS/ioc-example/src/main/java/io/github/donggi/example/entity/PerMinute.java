package io.github.donggi.example.entity;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import lombok.Data;

@Data
@Component
@Scope(value = "minute", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class PerMinute {
    private static int count = 0;
    private int num = PerMinute.count++; 
}
