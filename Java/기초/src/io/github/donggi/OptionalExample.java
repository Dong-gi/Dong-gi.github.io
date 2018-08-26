package io.github.donggi;

import java.util.Random;
import java.util.Optional;

public class OptionalExample {

    public static void main(String[] args) {
        for(var i = 0; i < 10; ++i) {
            var isOrNot = Optional.ofNullable(get());
            System.out.println("있을 수도, 없을 수도 있습니다 : " + isOrNot);
            System.out.println(isOrNot.orElse("하지만 그런 건 상관 없죠"));
        }
    }

    public static Object get() {
        return new Random().nextInt(2) == 0? null : "짠~"; 
    }
}