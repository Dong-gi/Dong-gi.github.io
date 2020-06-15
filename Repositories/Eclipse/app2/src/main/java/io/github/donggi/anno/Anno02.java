package io.github.donggi.anno;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import lombok.Data;

@Configuration
class Anno02Config {
    @Bean
    @Scope(scopeName = "prototype")
    public String anno02Message() {
        return String.format("Default Message By Annotation@%d", System.nanoTime());
    }
}

@Data
@Component
public class Anno02 {
    @Autowired
    private String anno02Message;
    @Autowired
    private Anno02[] anno02Arr;

    @Override
    public String toString() {
        return String.format("%s(message=%s)", this.getClass().getSimpleName(), anno02Message);
    }
}

@Component
class Anno02C1 extends Anno02 {}

@Component
class Anno02C2 extends Anno02 {}