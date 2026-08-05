package io.github.donggi.anno;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Anno25Test {
    @Test
    public void test() {
        try (var context = new AnnotationConfigApplicationContext(Anno25.class)) {
            assertTrue(context.getBeanNamesForType(io.github.donggi.scan.Data1.class).length == 0);
            assertTrue(context.getBeanNamesForType(io.github.donggi.scan.Data2.class).length == 0);
            assertTrue(context.getBeanNamesForType(io.github.donggi.scan.Data3.class).length == 0);
            assertTrue(context.getBeanNamesForType(io.github.donggi.scan.AService.class).length == 1);
            assertTrue(context.getBeanNamesForType(io.github.donggi.scan.BService.class).length == 1);
            assertTrue(context.getBeanNamesForType(io.github.donggi.scan.CService.class).length == 1);
        }
    }
}
