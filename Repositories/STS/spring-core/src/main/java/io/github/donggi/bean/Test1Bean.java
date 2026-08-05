package io.github.donggi.bean;

import java.util.Date;

import org.springframework.beans.factory.annotation.Value;

import io.github.donggi.anno.Test1Format;
import lombok.Data;

@Data
public class Test1Bean {
    @Test1Format(prefix = "<<", suffix = ">>")
    @Value("<<111111>>")
    public Integer num1;
    @Test1Format
    @Value("<222222>")
    private Integer num2;
    @Value("20200202222222")
    private Date date;
}