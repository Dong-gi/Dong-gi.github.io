package io.github.donggi.anno;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Anno28Test {
    @Test
    public void test() {
        try (var context = new AnnotationConfigApplicationContext(Anno28Config.class)) {
            assertTrue(context.getBean("msg1") != null);
            assertTrue(context.getBean("msg2") != null);
        }
    }
}
