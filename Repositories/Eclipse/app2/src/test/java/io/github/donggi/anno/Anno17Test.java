package io.github.donggi.anno;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Anno17Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/anno02.xml")) {
            for (var name : context.getBeanNamesForType(Anno17.class)) {
                var anno17 = (Anno17)context.getBean(name);
                log.info(anno17.getData().getMessage());
            }
        }
    }
/*
11:45:16.297 [main] INFO io.github.donggi.anno.Anno17Test - Anno17Data#Integer
*/
}
