package io.github.donggi.anno;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class Anno09Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/anno02.xml")) {
            for (var name : context.getBeanNamesForType(Anno09.class)) {
                var anno09 = (Anno09)context.getBean(name);
                assertTrue(anno09.getField1() == null);
                assertTrue(anno09.getField2() == null);
            }
        }
    }
}
