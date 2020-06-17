package io.github.donggi.anno;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Anno27Test {
    @Test
    public void test() {
        try (var context = new AnnotationConfigApplicationContext(Anno27Config1.class)) {
            assertTrue(context.getBean("msg1") != null);
            assertTrue(context.getBean("msg2") != null);
        }
    }
}
