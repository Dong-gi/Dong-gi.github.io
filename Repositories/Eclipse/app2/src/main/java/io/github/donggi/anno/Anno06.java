package io.github.donggi.anno;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Configuration
class Anno06Config {
    @Bean
    @Scope(scopeName = "prototype")
    public String anno06Message() {
        return String.format("@%d", System.nanoTime());
    }
}

@Getter
@Setter
@Component
public class Anno06 {
    @Autowired
    private String anno06Message;
    @Autowired
    private Map<String, Anno06> anno06Map;

    @Override
    public String toString() {
        return String.format("%s(message=%s)", this.getClass().getSimpleName(), anno06Message);
    }
}

@Component
class Anno06C1 extends Anno06 {}

@Component
class Anno06C2 extends Anno06 {}