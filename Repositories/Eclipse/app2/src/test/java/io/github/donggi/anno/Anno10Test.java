package io.github.donggi.anno;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class Anno10Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/anno02.xml")) {
            for (var name : context.getBeanNamesForType(Anno10.class)) {
                var anno10 = (Anno10)context.getBean(name);
                assertTrue(anno10.getAnno10Data().getName().equals("data2"));
            }
        }
    }
}