package io.github.donggi.bean;

import java.util.Date;

import lombok.Data;

@Data
public class Hello16 {
    private String message = new Date().toString();
}
