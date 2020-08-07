package io.github.donggi.jsr330;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Anno02Test {
    @Test
    public void test() {
        try (var context = new AnnotationConfigApplicationContext(Anno02Config.class)) {
            for (var name : context.getBeanNamesForType(Anno02.class)) {
                var bean = (Anno02)context.getBean(name);
                log.info(bean.toString());
            }
        }
    }
/*
15:16:38.706 [main] INFO io.github.donggi.jsr330.Anno02Test - Anno02(data=Anno02Data(message=Data 2))
*/
}
