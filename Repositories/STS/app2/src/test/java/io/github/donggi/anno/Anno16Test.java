package io.github.donggi.anno;

import org.junit.jupiter.api.Test;
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
16:15:04.712 [main] INFO io.github.donggi.anno.Anno16Test - Anno16Data#String
*/
}
