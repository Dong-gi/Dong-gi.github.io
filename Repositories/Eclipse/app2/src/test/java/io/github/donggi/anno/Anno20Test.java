package io.github.donggi.anno;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Anno20Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/anno02.xml")) {
            for (var name : context.getBeanNamesForType(Anno20.class)) {
                var anno20 = (Anno20)context.getBean(name);
                log.info(anno20.toString());
            }
        }
    }
/*
14:17:36.094 [main] INFO io.github.donggi.anno.Anno20Test - Anno20(javaHome=C:\Program Files\Java\jdk-12.0.2 <- Here, map={Apple=100, Banana=10})
*/
}
