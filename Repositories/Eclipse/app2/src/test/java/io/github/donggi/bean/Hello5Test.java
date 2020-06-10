package io.github.donggi.bean;

import java.util.Arrays;

import org.apache.commons.collections4.MapUtils;
import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello5Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans8.xml")) {
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
13:56:04.549 [main] INFO io.github.donggi.bean.Hello5Test - [Arr 메시지 1, null, Arr 메시지 2]
13:56:04.550 [main] INFO io.github.donggi.bean.Hello5Test - [List 메시지 1, List 메시지 2]
13:56:04.550 [main] INFO io.github.donggi.bean.Hello5Test - [Set 메시지 1, Set 메시지 2, Set 메시지 3]
map = 
{
    key1 = value2
    key2 = value1
}
prop1 = 
{
    key1 = value2
    key2 = value2
}
prop2 = 
{
    key1 = value2
    key2 = value2
}
*/
}
