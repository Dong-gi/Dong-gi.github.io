package io.github.donggi.bean;

import org.springframework.beans.factory.annotation.Value;

import io.github.donggi.anno.Test2Constraint;
import lombok.Data;

@Data
public class Test2Bean3 {
    @Test2Constraint
    @Value("hello")
    private String msg;
}