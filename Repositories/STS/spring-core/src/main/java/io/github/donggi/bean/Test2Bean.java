package io.github.donggi.bean;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class Test2Bean {
    @NotNull
    @Size(max=32)
    private String msg;
    @Min(10)
    private int cost;
}