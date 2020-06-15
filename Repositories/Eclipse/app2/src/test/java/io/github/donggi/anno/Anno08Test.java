package io.github.donggi.anno;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Anno08Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/anno02.xml")) {
            for (var name : context.getBeanNamesForType(Anno08.class)) {
                var anno08 = (Anno08)context.getBean(name);
                assertTrue(anno08.getAnno08Array() != null);
                assertTrue(anno08.getAnno08List() != null);
                log.info(anno08.getAnno08Array().length);
                log.info(anno08.getAnno08List().size());
            }
        }
    }
/*
13:39:43.801 [main] INFO io.github.donggi.anno.Anno08Test - 0
13:39:43.801 [main] INFO io.github.donggi.anno.Anno08Test - 0
*/
}
