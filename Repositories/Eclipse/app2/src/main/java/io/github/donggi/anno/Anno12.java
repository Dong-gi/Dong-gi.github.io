package io.github.donggi.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
@interface Anno12Val {
    String value();
}

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Configuration
class ForAnno12 {

    private String name;

    @Bean
    public ForAnno12 data1() {
        return new ForAnno12("data1");
    }
    @Bean(name="ForAnno12-2")
    public ForAnno12 data2() {
        return new ForAnno12("data2");
    }
}

@Getter
@Setter
@Component
public class Anno12 {

    private ForAnno12 anno12Data;

    public Anno12(@Anno12Val("ForAnno12-2") ForAnno12 data) {
        this.anno12Data = data;
    }
}