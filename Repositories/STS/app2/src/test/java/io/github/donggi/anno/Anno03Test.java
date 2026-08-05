package io.github.donggi.anno;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Anno03Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/anno02.xml")) {
            var anno03 = (Anno03)context.getBean("anno03");
            log.info(anno03);
        }
    }
/*
11:19:17.461 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'anno03'
11:19:17.473 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'anno03Message'
11:19:17.473 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'anno03Config'
11:19:17.483 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Autowiring by type from bean name 'anno03' via constructor to bean named 'anno03Message'
11:19:17.511 [main] INFO io.github.donggi.anno.Anno03Test - Anno03(anno03Message=Default message for Anno03)
*/
}
