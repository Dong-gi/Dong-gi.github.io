package io.github.donggi.anno;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Anno07Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/anno02.xml")) {
            for (var name : context.getBeanNamesForType(Anno07.class)) {
                var anno07 = (Anno07)context.getBean(name);
                log.infof("↓ Bean {}'s map", name);
                anno07.getAnno07Map().forEach((key, value) -> log.infof("key={}, value={}", key, value));
            }
        }
    }
/*
11:48:52.770 [main] INFO io.github.donggi.anno.Anno07Test - ↓ Bean anno07's map
11:48:52.771 [main] DEBUG org.springframework.context.support.FileSystemXmlApplicationContext - Closing org.springframework.context.support.FileSystemXmlApplicationContext@4470f8a6, started on Mon Jun 15 11:48:51 KST 2020
*/
}
