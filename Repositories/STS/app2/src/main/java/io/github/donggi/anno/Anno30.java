package io.github.donggi.anno;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Configuration
class Anno30Config {
    @Bean
    public Anno30 anno30() {
        return new Anno30();
    }
}

@Data
class Anno30Data {
    String name;
    String domain;
}

@Data
public class Anno30 {
    @Value("test@abc.com")
    private Anno30Data data;
}