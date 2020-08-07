package io.github.donggi.jsr330;

import java.util.Optional;

import javax.inject.Inject;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import lombok.Data;

@Configuration
class Anno04Config {
    @Bean
    public static Anno04 anno04() {
        return new Anno04();
    }
}

class Anno04Data1 {}
class Anno04Data2 {}

@Data
public class Anno04 {
    @Inject
    private Optional<Anno04Data1> data1;
    @Inject
    @Nullable
    private Anno04Data2 data2;    
}