package io.github.donggi.bean;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import lombok.Data;

@Data
public class Test2Bean {
    @NotNull
    @Size(max=32)
    private String msg;
    @Min(10)
    private int cost;
}