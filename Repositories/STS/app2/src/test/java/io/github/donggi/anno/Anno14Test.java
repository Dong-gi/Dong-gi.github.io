package io.github.donggi.anno;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Anno14Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/anno02.xml")) {
            for (var name : context.getBeanNamesForType(Anno14.class)) {
                var anno14 = (Anno14)context.getBean(name);
                log.info(anno14.getData().toString());
            }
        }
    }
/*
10:57:18.679 [main] INFO io.github.donggi.anno.Anno14Test - ForAnno14C(name=ForAnno14C)
*/
}
