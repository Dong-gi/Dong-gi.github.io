package io.github.donggi.anno;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(Anno27Config2.class)
class Anno27Config1 {
    @Bean
    public String msg1() {
        return "msg1";
    }
}

@Configuration
class Anno27Config2 {
    @Bean
    public String msg2() {
        return "msg2";
    }
}

public class Anno27 {}