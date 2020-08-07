package io.github.donggi.jsr330;

import javax.inject.Inject;
import javax.inject.Provider;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import lombok.Data;

@Configuration
class Anno03Config {
    @Bean
    @Scope("prototype")
    public static Anno03Data anno03Data() {
        return new Anno03Data();
    }
    @Bean
    public static Anno03 anno03() {
        return new Anno03();
    }
}

@Data
class Anno03Data {
    private String message = "Default Message";
    private long createdTime = System.nanoTime();
}

@Data
public class Anno03 {
    @Inject
    private Provider<Anno03Data> data;
}