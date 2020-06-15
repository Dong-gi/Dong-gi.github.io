package io.github.donggi.anno;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Anno04Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/anno02.xml")) {
            var anno04 = (Anno04)context.getBean("anno04");
            log.info(anno04);
        }
    }
/*
11:24:01.618 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'anno04'
11:24:01.619 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'anno04Message'
11:24:01.619 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'anno04Config'
11:24:01.631 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'anno04Time'
11:24:01.637 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Autowiring by type from bean name 'anno04' via constructor to bean named 'anno04Message'
11:24:01.638 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Autowiring by type from bean name 'anno04' via constructor to bean named 'anno04Time'
11:24:01.676 [main] INFO io.github.donggi.anno.Anno04Test - Anno04(anno04Message=Default message for Anno04@Mon Jun 15 11:24:01 KST 2020)
*/
}
