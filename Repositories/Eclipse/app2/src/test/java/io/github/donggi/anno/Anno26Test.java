package io.github.donggi.anno;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Anno26Test {
    @Test
    public void test() {
        try (var context = new AnnotationConfigApplicationContext(Anno26Config.class)) {
            assertTrue((int) context.getBean("count1") == 4);
            assertTrue((int) context.getBean("count2") == 1);
        }
    }
}
