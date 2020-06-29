package io.github.donggi;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.test.context.ActiveProfiles;

import io.github.donggi.bean.Test1Bean;
import lombok.extern.slf4j.Slf4j;

@ActiveProfiles("test1")
@SpringBootTest
@Slf4j
class Test1 {
    
    @Autowired
    private ApplicationContext context;
    @Autowired
    private FormattingConversionService conversionService;
    @Autowired
    private Test1Bean test1Bean;
    
    @Test
    void test() throws NoSuchFieldException, SecurityException {
        assertTrue(context.getEnvironment().getActiveProfiles()[0].equals("test1"));
        log.info(test1Bean.getNum1().toString());
        log.info(test1Bean.getNum2().toString());
        log.info(test1Bean.getDate().toString());
        log.info((String) conversionService.convert(test1Bean.getNum1(), new TypeDescriptor(test1Bean.getClass().getField("num1")), TypeDescriptor.valueOf(String.class)));
        log.info(conversionService.convert(test1Bean.getNum2(), String.class));
        log.info(conversionService.convert(test1Bean.getDate(), String.class));
    }
/*
2020-06-29 10:59:52.148  INFO 282008 --- [           main] io.github.donggi.Test1                   : 111111
2020-06-29 10:59:52.148  INFO 282008 --- [           main] io.github.donggi.Test1                   : 222222
2020-06-29 10:59:52.148  INFO 282008 --- [           main] io.github.donggi.Test1                   : Sun Feb 02 22:22:22 KST 2020
2020-06-29 10:59:52.149  INFO 282008 --- [           main] io.github.donggi.Test1                   : <<111111>>
2020-06-29 10:59:52.149  INFO 282008 --- [           main] io.github.donggi.Test1                   : 222222
2020-06-29 10:59:52.149  INFO 282008 --- [           main] io.github.donggi.Test1                   : 20200202222222
 */
}
