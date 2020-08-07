package io.github.donggi.anno;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
class Anno15Data<T> {
    private String message;

    public Anno15Data(String message) {
        this.message = String.format("%s#%s", this.getClass().getSimpleName(), message);
    }
}

@Configuration
class Anno15Config {
    @Bean
    public static Anno15Data<String> anno15data1() {
        return new Anno15Data<String>("String");
    }
    @Bean
    public static Anno15Data<Integer> anno15data2() {
        return new Anno15Data<Integer>("Integer");
    }
}

@Getter
@Setter
@Component
public class Anno15 {
    @Autowired
    private Anno15Data<Integer> data;
}