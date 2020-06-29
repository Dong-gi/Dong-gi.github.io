package io.github.donggi.bean;

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class Test2Bean2 {
    @NotNull
    @Valid
    private Test2Bean test2Bean;
    @NotEmpty
    private List<@Valid Test2Bean> list1;
    @NotEmpty
    private List<@Valid Test2Bean> list2;
}