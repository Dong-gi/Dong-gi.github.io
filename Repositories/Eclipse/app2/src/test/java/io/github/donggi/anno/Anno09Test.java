package io.github.donggi.anno;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class Anno09Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/anno02.xml")) {
            for (var name : context.getBeanNamesForType(Anno09.class)) {
                var anno09 = (Anno09)context.getBean(name);
                assertTrue(anno09.getAnno09field1() == null);
                assertTrue(anno09.getAnno09field2() == null);
            }
        }
    }
}
