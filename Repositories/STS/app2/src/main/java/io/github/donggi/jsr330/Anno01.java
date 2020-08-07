package io.github.donggi.jsr330;

import javax.inject.Inject;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Configuration
class Anno01Config {
    @Bean
    public static Anno01Data anno01Data() {
        return new Anno01Data();
    }
    @Bean
    public static Anno01 anno01() {
        return new Anno01();
    }
}

@Data
class Anno01Data {
    private String message = "Default Message";
}

@Data
public class Anno01 {
    @Inject
    private Anno01Data data;
}