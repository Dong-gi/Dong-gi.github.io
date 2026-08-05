package io.github.donggi.anno;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Anno18Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/anno02.xml")) {
            for (var name : context.getBeanNamesForType(Anno18.class)) {
                var anno18 = (Anno18)context.getBean(name);
                log.info(anno18.toString());
            }
        }
    }
/*
13:56:29.030 [main] INFO io.github.donggi.anno.Anno18Test - Anno18(sayHello=true, msg=안녕 세상, noMsg=default msg : 안녕, num=12345)
*/
}
