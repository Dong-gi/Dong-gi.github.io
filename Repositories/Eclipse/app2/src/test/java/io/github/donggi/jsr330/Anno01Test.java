package io.github.donggi.jsr330;

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Anno01Test {
    @Test
    public void test() {
        try (var context = new AnnotationConfigApplicationContext(Anno01Config.class)) {
            for (var name : context.getBeanNamesForType(Anno01.class)) {
                var bean = (Anno01)context.getBean(name);
                log.info(bean.toString());
            }
        }
    }
/*
15:13:07.333 [main] INFO io.github.donggi.jsr330.Anno01Test - Anno01(data=Anno01Data(message=Default Message))
*/
}
