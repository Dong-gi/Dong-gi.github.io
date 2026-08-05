package io.github.donggi.anno;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Anno29Test {
    @Test
    public void test() {
        try (var context = new AnnotationConfigApplicationContext(Anno29Config.class)) {
            log.info(context.getBean("anno29").toString());
        }
    }
/*
17:43:57.489 [main] DEBUG org.springframework.validation.ValidationUtils - Invoking validator [io.github.donggi.anno.Anno29DataValidator@7203c7ff]
17:43:57.572 [main] DEBUG org.springframework.validation.ValidationUtils - Validator found 1 errors
17:43:57.572 [main] INFO io.github.donggi.anno.Anno29Config - org.springframework.validation.BeanPropertyBindingResult: 1 errors
Field error in object 'anno29' on field 'data.email': rejected value [null]; codes [is.empty.anno29.data.email,is.empty.data.email,is.empty.email,is.empty.java.lang.String,is.empty]; arguments []; default message [null]
17:43:57.582 [main] INFO io.github.donggi.anno.Anno29Config - is.empty.email message : 이메일이 없습니다.
17:43:57.626 [main] INFO io.github.donggi.anno.Anno29Test - Anno29(data=Anno29Data(name=my name, email=null))
*/
}
