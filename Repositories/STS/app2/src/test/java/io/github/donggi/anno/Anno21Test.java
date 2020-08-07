package io.github.donggi.anno;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Anno21Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/anno02.xml")) {
            for (var name : context.getBeanNamesForType(Anno21.class)) {
                var anno21 = (Anno21)context.getBean(name);
                log.info(anno21.toString());
            }
        }
    }
/*
Anno21 Bean Initialized...
14:29:33.219 [main] INFO io.github.donggi.anno.Anno21Test - io.github.donggi.anno.Anno21@6f70f32f
Anno21 Bean Destroyed...
*/
}
