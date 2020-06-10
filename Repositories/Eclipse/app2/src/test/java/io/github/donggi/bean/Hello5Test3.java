package io.github.donggi.bean;

import java.util.Arrays;

import org.apache.commons.collections4.MapUtils;
import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello5Test3 {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans10.xml")) {
            var hello5 = (Hello5) context.getBean("hello5");
            log.info(Arrays.toString(hello5.getMessageArr()));
            log.info(Arrays.toString(hello5.getMessageList().toArray()));
            log.info(Arrays.toString(hello5.getMessageSet().toArray()));
            MapUtils.verbosePrint(System.out, "map", hello5.getMessageMap());
            MapUtils.verbosePrint(System.out, "prop1", hello5.getMessageProps1());
            MapUtils.verbosePrint(System.out, "prop2", hello5.getMessageProps2());
        }
    }
/*
13:58:37.145 [main] INFO io.github.donggi.bean.Hello5Test3 - [메시지 1, 메시지 1, , 메시지 2, null]
13:58:37.145 [main] INFO io.github.donggi.bean.Hello5Test3 - [메시지 1, 메시지 1, , 메시지 2, null]
13:58:37.145 [main] INFO io.github.donggi.bean.Hello5Test3 - [, null, 메시지 1, 메시지 2]
map = 
{
    key1 = value1
    key2 = value2
}
prop1 = 
{
    key1 = value1
    key2 = value2
}
prop2 = null
*/
}
