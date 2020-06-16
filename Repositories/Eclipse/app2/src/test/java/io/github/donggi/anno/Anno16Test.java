package io.github.donggi.anno;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Anno16Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/anno02.xml")) {
            for (var name : context.getBeanNamesForType(Anno16.class)) {
                var anno16 = (Anno16)context.getBean(name);
                log.info(anno16.getData().getMessage());
            }
        }
    }
/*
11:27:41.469 [main] INFO io.github.donggi.anno.Anno15Test - Anno15Data#Integer
*/
}
