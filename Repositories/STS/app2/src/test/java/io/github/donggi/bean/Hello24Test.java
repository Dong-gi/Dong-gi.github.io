package io.github.donggi.bean;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello24Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans39.xml")) {
            log.info("Nothing happened...");
        }
    }
/*
16:49:45.001 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'io.github.donggi.bean.Hello24$C1#0'
Hello24$C1 Bean Created...
16:49:45.023 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'io.github.donggi.bean.Hello24$C2#0'
16:49:45.023 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'io.github.donggi.bean.Hello24$C1#1'
Hello24$C1 Bean Created...
16:49:45.033 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'io.github.donggi.bean.Hello24$C2#1'
16:49:45.083 [main] INFO io.github.donggi.bean.Hello24Test - Nothing happened...
16:49:45.083 [main] DEBUG org.springframework.context.support.FileSystemXmlApplicationContext - Closing org.springframework.context.support.FileSystemXmlApplicationContext@61386958, started on Thu Jun 11 16:49:44 KST 2020
Hello24$C2 Bean Destroyed...
Hello24$C2 Bean Destroyed...
*/
}
