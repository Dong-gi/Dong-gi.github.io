package io.github.donggi.anno;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Anno08Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/anno02.xml")) {
            for (var name : context.getBeanNamesForType(Anno08.class)) {
                var anno08 = (Anno08)context.getBean(name);
                assertTrue(anno08.getArr() != null);
                assertTrue(anno08.getList() != null);
                log.info(anno08.getArr().length);
                log.info(anno08.getList().size());
            }
        }
    }
/*
13:39:43.801 [main] INFO io.github.donggi.anno.Anno08Test - 0
13:39:43.801 [main] INFO io.github.donggi.anno.Anno08Test - 0
*/
}
