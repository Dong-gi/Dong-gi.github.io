package io.github.donggi.jsr330;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Anno03Test {
    @Test
    public void test() {
        try (var context = new AnnotationConfigApplicationContext(Anno03Config.class)) {
            for (var name : context.getBeanNamesForType(Anno03.class)) {
                var currentTime = System.nanoTime();
                var bean = (Anno03)context.getBean(name);
                log.info(bean.getData().get().toString());
                assertTrue(currentTime < bean.getData().get().getCreatedTime());
            }
        }
    }
/*
15:26:58.909 [main] INFO io.github.donggi.jsr330.Anno03Test - Anno03Data(message=Default Message, createdTime=145116019039200)
*/
}
