package io.github.donggi;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.validation.ValidatorFactory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import io.github.donggi.bean.Test2Bean;
import io.github.donggi.bean.Test2Bean2;
import io.github.donggi.bean.Test2Bean3;
import lombok.extern.slf4j.Slf4j;

@ActiveProfiles("test2")
@SpringBootTest
@Slf4j
class Test2 {
    
    @Autowired
    private ApplicationContext context;
    @Autowired
    private ValidatorFactory validator;
    @Autowired
    private Test2Bean test2Bean;
    @Autowired
    private Test2Bean2 test2Bean2;
    @Autowired
    private Test2Bean3 test2Bean3;
    
    @Test
    void test() throws NoSuchFieldException, SecurityException {
        assertTrue(context.getEnvironment().getActiveProfiles()[0].equals("test2"));
        log.info("Test2Bean");
        var result1 = validator.getValidator().validate(test2Bean);
        result1.forEach(x -> log.info(x.toString()));
        log.info("Test2Bean2");
        var result2 = validator.getValidator().validate(test2Bean2);
        result2.forEach(x -> log.info(x.toString()));
        log.info("Test2Bean3");
        var result3 = validator.getValidator().validate(test2Bean3);
        result3.forEach(x -> log.info(x.toString()));
    }
/*
2020-06-29 15:29:16.015  INFO 320304 --- [           main] io.github.donggi.Test2                   : Test2Bean
2020-06-29 15:29:16.179  INFO 320304 --- [           main] io.github.donggi.Test2                   : ConstraintViolationImpl{interpolatedMessage='널이어서는 안됩니다', propertyPath=msg, rootBeanClass=class io.github.donggi.bean.Test2Bean, messageTemplate='{javax.validation.constraints.NotNull.message}'}
2020-06-29 15:29:16.179  INFO 320304 --- [           main] io.github.donggi.Test2                   : ConstraintViolationImpl{interpolatedMessage='10 이상이어야 합니다', propertyPath=cost, rootBeanClass=class io.github.donggi.bean.Test2Bean, messageTemplate='{javax.validation.constraints.Min.message}'}
2020-06-29 15:29:16.179  INFO 320304 --- [           main] io.github.donggi.Test2                   : Test2Bean2
2020-06-29 15:29:16.208  INFO 320304 --- [           main] io.github.donggi.Test2                   : ConstraintViolationImpl{interpolatedMessage='10 이상이어야 합니다', propertyPath=test2Bean.cost, rootBeanClass=class io.github.donggi.bean.Test2Bean2, messageTemplate='{javax.validation.constraints.Min.message}'}
2020-06-29 15:29:16.208  INFO 320304 --- [           main] io.github.donggi.Test2                   : ConstraintViolationImpl{interpolatedMessage='널이어서는 안됩니다', propertyPath=test2Bean.msg, rootBeanClass=class io.github.donggi.bean.Test2Bean2, messageTemplate='{javax.validation.constraints.NotNull.message}'}
2020-06-29 15:29:16.208  INFO 320304 --- [           main] io.github.donggi.Test2                   : ConstraintViolationImpl{interpolatedMessage='널이어서는 안됩니다', propertyPath=list1[0].msg, rootBeanClass=class io.github.donggi.bean.Test2Bean2, messageTemplate='{javax.validation.constraints.NotNull.message}'}
2020-06-29 15:29:16.208  INFO 320304 --- [           main] io.github.donggi.Test2                   : ConstraintViolationImpl{interpolatedMessage='10 이상이어야 합니다', propertyPath=list1[0].cost, rootBeanClass=class io.github.donggi.bean.Test2Bean2, messageTemplate='{javax.validation.constraints.Min.message}'}
2020-06-29 15:29:16.208  INFO 320304 --- [           main] io.github.donggi.Test2                   : ConstraintViolationImpl{interpolatedMessage='비어 있을 수 없습니다', propertyPath=list2, rootBeanClass=class io.github.donggi.bean.Test2Bean2, messageTemplate='{javax.validation.constraints.NotEmpty.message}'}
2020-06-29 15:29:16.209  INFO 320304 --- [           main] io.github.donggi.Test2                   : Test2Bean3
2020-06-29 15:29:16.230  INFO 320304 --- [           main] io.github.donggi.Test2                   : ConstraintViolationImpl{interpolatedMessage='문자열 길이는 짝수여야 합니다. But hello', propertyPath=msg, rootBeanClass=class io.github.donggi.bean.Test2Bean3, messageTemplate='문자열 길이는 짝수여야 합니다. But hello'}
 */
}
