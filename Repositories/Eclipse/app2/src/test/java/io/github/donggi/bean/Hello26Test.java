package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello26Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans41.xml")) {
            log.info("Nothing happened...");
        }
    }
/*
17:15:37.800 [main] INFO io.github.donggi.bean.Hello26 - You named me www, thanks!
17:15:37.846 [main] INFO io.github.donggi.bean.Hello26Test - Nothing happened...
*/
}
