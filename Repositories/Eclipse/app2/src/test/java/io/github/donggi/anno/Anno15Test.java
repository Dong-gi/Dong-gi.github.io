package io.github.donggi.anno;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Anno15Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/anno02.xml")) {
            for (var name : context.getBeanNamesForType(Anno15.class)) {
                var anno15 = (Anno15)context.getBean(name);
                log.info(anno15.getData().getMessage());
            }
        }
    }
/*
11:37:47.181 [main] INFO io.github.donggi.anno.Anno16Test - Anno16Data#String
*/
}
