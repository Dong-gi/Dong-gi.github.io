package io.github.donggi.anno;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
class ForAnno10 {

    private String name;

    @Bean
    public ForAnno10 data1() {
        return new ForAnno10("data1");
    }
    @Bean
    @Primary
    public ForAnno10 data2() {
        return new ForAnno10("data2");
    }
}

@Getter
@Setter
@Component
public class Anno10 {

    @Autowired
    private ForAnno10 anno10Data;
}