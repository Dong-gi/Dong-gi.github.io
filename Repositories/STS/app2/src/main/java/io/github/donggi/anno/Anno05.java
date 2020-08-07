package io.github.donggi.anno;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Configuration
class Anno05Config {
    @Bean
    @Scope(scopeName = "prototype")
    public String anno05Message() {
        return String.format("@%d", System.nanoTime());
    }
}

@Getter
@Setter
@Component
public class Anno05 {
    @Autowired
    private String anno05Message;
    @Autowired
    private Set<Anno05> anno05Set;

    @Override
    public String toString() {
        return String.format("%s(message=%s)", this.getClass().getSimpleName(), anno05Message);
    }
}

@Component
class Anno05C1 extends Anno05 {}

@Component
class Anno05C2 extends Anno05 {}