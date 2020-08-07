package io.github.donggi.anno;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Anno01Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/anno01.xml")) {
            log.info(context.getBean("anno01"));
        }
    }
/*
11:17:38.820 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'java.lang.String#0'
11:17:38.882 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'anno01'
11:17:38.938 [main] INFO io.github.donggi.anno.Anno01Test - Anno01(anno01Message=Default Message By XML)
*/
}
