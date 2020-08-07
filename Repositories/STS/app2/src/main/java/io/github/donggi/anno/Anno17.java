package io.github.donggi.anno;

import javax.annotation.Resource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
class Anno17Data<T> {
    private String message;

    public Anno17Data(String message) {
        this.message = String.format("%s#%s", this.getClass().getSimpleName(), message);
    }
}

@Configuration
class Anno17Config {
    @Bean
    public static Anno17Data<String> anno17data1() {
        return new Anno17Data<String>("String");
    }
    @Bean
    public static Anno17Data<Integer> anno17data2() {
        return new Anno17Data<Integer>("Integer");
    }
}

@Getter
@Setter
@Component
public class Anno17 {
    @Resource
    private Anno17Data<Integer> data;
}