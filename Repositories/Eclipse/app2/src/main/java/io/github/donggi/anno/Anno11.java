package io.github.donggi.anno;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Configuration
class ForAnno11 {

    private String name;

    @Bean
    public ForAnno11 data1() {
        return new ForAnno11("data1");
    }
    @Bean(name="ForAnno11-2")
    public ForAnno11 data2() {
        return new ForAnno11("data2");
    }
}

@Getter
@Setter
@Component
public class Anno11 {

    private ForAnno11 anno11Data;

    public Anno11(@Qualifier("ForAnno11-2") ForAnno11 data) {
        this.anno11Data = data;
    }
}