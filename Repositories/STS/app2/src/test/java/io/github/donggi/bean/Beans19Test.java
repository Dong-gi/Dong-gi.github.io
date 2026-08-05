package io.github.donggi.bean;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Beans19Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans19.xml")) {
            var list1 = List.class.cast(context.getBean("list1"));
            var list2 = List.class.cast(context.getBean("list2"));
            log.info(list1.size());
            log.info(list2.size());
            log.info(list2.getClass());
            log.info(Arrays.toString(list1.toArray()));
            log.info(Arrays.toString(list2.toArray()));
        }
    }
/*
13:39:33.598 [main] INFO io.github.donggi.bean.Beans19Test - 4
13:39:33.599 [main] INFO io.github.donggi.bean.Beans19Test - 0
13:39:33.599 [main] INFO io.github.donggi.bean.Beans19Test - class $java.util.ArrayList$$EnhancerBySpringCGLIB$$4c577b42
13:39:33.599 [main] INFO io.github.donggi.bean.Beans19Test - [메시지 1, 메시지 1, , 메시지 2]
13:39:33.599 [main] INFO io.github.donggi.bean.Beans19Test - [메시지 1, 메시지 1, , 메시지 2]
*/
}
