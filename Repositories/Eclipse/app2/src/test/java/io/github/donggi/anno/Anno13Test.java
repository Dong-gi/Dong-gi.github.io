package io.github.donggi.anno;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Anno13Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/anno02.xml")) {
            for (var name : context.getBeanNamesForType(Anno13.class)) {
                var anno13 = (Anno13)context.getBean(name);
                log.info(anno13.getData().toString());
            }
        }
    }
/*
15:17:21.447 [main] INFO io.github.donggi.anno.Anno13Test - ForAnno13B(name=ForAnno13B)
*/
}
