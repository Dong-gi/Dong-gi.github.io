package io.github.donggi.anno;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Configuration
class Anno07Config {
    @Bean
    @Scope(scopeName = "prototype")
    public String anno07Message() {
        return String.format("@%d", System.nanoTime());
    }
}

@Getter
@Setter
@Component
public class Anno07 {
    @Autowired
    private String anno07Message;
    @Autowired(required = false)
    private Map<String, Anno07> anno07Map = new HashMap<>();

    @Override
    public String toString() {
        return String.format("%s(message=%s)", this.getClass().getSimpleName(), anno07Message);
    }
}