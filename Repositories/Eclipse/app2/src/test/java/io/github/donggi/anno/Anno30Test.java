package io.github.donggi.anno;

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Anno30Test {
    @Test
    public void test() {
        try (var context = new AnnotationConfigApplicationContext(Anno30Config.class)) {
            log.info(context.getBean("anno30").toString());
        }
    }
/*
13:28:23.908 [main] INFO io.github.donggi.anno.Anno30Test - Anno30(data=Anno30Data(name=test, domain=abc.com))
*/
}
