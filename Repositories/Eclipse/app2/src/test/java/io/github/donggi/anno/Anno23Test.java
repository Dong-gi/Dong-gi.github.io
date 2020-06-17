package io.github.donggi.anno;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Anno23Test {
    @Test
    public void test() {
        try (var context = new AnnotationConfigApplicationContext(Anno23.class)) {
            assertTrue(context.getBeanNamesForType(io.github.donggi.scan.Data1.class).length == 1);
            assertTrue(context.getBeanNamesForType(io.github.donggi.scan.Data2.class).length == 0);
            assertTrue(context.getBeanNamesForType(io.github.donggi.scan.Data3.class).length == 0);
        }
    }
}
