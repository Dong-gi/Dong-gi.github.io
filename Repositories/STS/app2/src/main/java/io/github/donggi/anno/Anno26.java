package io.github.donggi.anno;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class Anno26Config {
    private static int count1 = 0;
    private static int count2 = 0;

    @Bean
    public static Anno26 anno26A() {
        count1++;
        return new Anno26();
    }
    @Bean
    public static int count1() {
        anno26A();
        anno26A();
        anno26A();
        return count1;
    }

    @Bean
    public Anno26 anno26B() {
        count2++;
        return new Anno26();
    }
    @Bean
    public int count2() {
        anno26B();
        anno26B();
        anno26B();
        return count2;
    }
}

public class Anno26 {}