package io.github.donggi.anno;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Anno24Test {
    @Test
    public void test() {
        try (var context = new AnnotationConfigApplicationContext(Anno24.class)) {
            assertTrue(context.getBeanNamesForType(io.github.donggi.scan.AService.class).length == 0);
            assertTrue(context.getBeanNamesForType(io.github.donggi.scan.BService.class).length == 1);
            assertTrue(context.getBeanNamesForType(io.github.donggi.scan.CService.class).length == 0);
        }
    }
}
