package io.github.donggi.anno;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class Anno11Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/anno02.xml")) {
            for (var name : context.getBeanNamesForType(Anno11.class)) {
                var anno11 = (Anno11)context.getBean(name);
                assertTrue(anno11.getData().getName().equals("data2"));
            }
        }
    }
}
