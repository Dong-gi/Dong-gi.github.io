package io.github.donggi.anno;

import org.junit.jupiter.api.Test;
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
16:14:24.619 [main] INFO io.github.donggi.anno.Anno15Test - Anno15Data#Integer
*/
}
