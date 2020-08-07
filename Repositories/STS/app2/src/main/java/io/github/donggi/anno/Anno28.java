package io.github.donggi.anno;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

@Configuration
@ImportResource("file:src/main/resource/anno28.xml")
class Anno28Config {
    @Bean
    public String msg1() {
        return "msg1";
    }
}

public class Anno28 {}