package io.github.donggi.anno;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import io.github.donggi.rare.Rare1;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Anno22Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/anno02.xml")) {
            for (var name : context.getBeanNamesForType(Rare1.class)) {
                var rare = (Rare1)context.getBean(name);
                log.info(rare.toString());
            }
        }
    }
/*
17:18:48.544 [main] INFO io.github.donggi.anno.Anno22Test - You're lucky!!
*/
}
