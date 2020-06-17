package io.github.donggi.jsr330;

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Anno04Test {
    @Test
    public void test() {
        try (var context = new AnnotationConfigApplicationContext(Anno04Config.class)) {
            for (var name : context.getBeanNamesForType(Anno04.class)) {
                var bean = (Anno04)context.getBean(name);
                log.info(bean.toString());
            }
        }
    }
/*
15:32:19.388 [main] INFO io.github.donggi.jsr330.Anno04Test - Anno04(data1=Optional.empty, data2=null)
*/
}
