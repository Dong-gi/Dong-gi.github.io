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


enum Anno14ValType {
    TYPE1, TYPE2
}

@Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
@interface Anno14Val {
    Anno14ValType type();
}

@Data
@Configuration
@Anno14Val(type = Anno14ValType.TYPE1)
class ForAnno14A {
    private String name = ForAnno14A.class.getSimpleName();
}

@Data
@Configuration
@Anno14Val(type = Anno14ValType.TYPE2)
class ForAnno14B {
    private String name = ForAnno14B.class.getSimpleName();
}

@Getter
@Setter
@Component
public class Anno14 {

    private Object data;

    public Anno14(@Anno14Val(type = Anno14ValType.TYPE2) Object data) {
        this.data = data;
    }
}