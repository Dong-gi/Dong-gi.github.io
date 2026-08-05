package io.github.donggi.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;


@Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
@interface Anno13Val1 {}

@Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
@interface Anno13Val2 {}


@Data
@Configuration
@Anno13Val1
class ForAnno13A {
    private String name = ForAnno13A.class.getSimpleName();
}

@Data
@Configuration
@Anno13Val2
class ForAnno13B {
    private String name = ForAnno13B.class.getSimpleName();
}

@Getter
@Setter
@Component
public class Anno13 {

    private Object data;

    public Anno13(@Anno13Val2 Object data) {
        this.data = data;
    }
}