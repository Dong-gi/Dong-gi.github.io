package io.github.donggi.anno;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
public class Anno20 {
    @Value("#{systemProperties['java.home'] + ' <- Here'}")
    private String javaHome;
    @Value("#{{'Apple':100,'Banana':10}}")
    private Map<String, Integer> map;
}