package io.github.donggi.bean;

import lombok.Data;

@Data
public class Hello19 {
    private Nested1 foo = new Nested1();

    @Data
    public static class Nested1 {
        private Nested2 bar = new Nested2();
    }

    @Data
    public static class Nested2 {
        private String msg;
    }
}
