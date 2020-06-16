package io.github.donggi.anno;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Anno19Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/anno02.xml")) {
            for (var name : context.getBeanNamesForType(Anno19.class)) {
                var anno19 = (Anno19)context.getBean(name);
                log.info(anno19.toString());
            }
        }
    }
/*
14:11:20.000 [main] INFO io.github.donggi.anno.Anno19Test - Anno19(data=Anno19Data(x=123, y=456, z=789))
*/
}
