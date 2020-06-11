package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello27Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans42.xml")) {
            log.info("Nothing happened...");
        }
    }
/*
18:00:33.809 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'io.github.donggi.bean.Hello27#0'
18:00:33.810 [main] INFO io.github.donggi.bean.Hello27 - Before init callbacks of io.github.donggi.bean.Hello27#0
18:00:33.810 [main] INFO io.github.donggi.bean.Hello27 - After init callbacks of io.github.donggi.bean.Hello27#0
18:00:33.810 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'io.github.donggi.bean.Hello27#1'
18:00:33.811 [main] INFO io.github.donggi.bean.Hello27 - Before init callbacks of io.github.donggi.bean.Hello27#1
18:00:33.811 [main] INFO io.github.donggi.bean.Hello27 - After init callbacks of io.github.donggi.bean.Hello27#1
18:00:33.854 [main] INFO io.github.donggi.bean.Hello27Test - Nothing happened...
*/
}
