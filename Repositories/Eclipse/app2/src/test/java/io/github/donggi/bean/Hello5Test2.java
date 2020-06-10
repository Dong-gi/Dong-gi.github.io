package io.github.donggi.bean;

import java.util.Arrays;

import org.apache.commons.collections4.MapUtils;
import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello5Test2 {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans9.xml")) {
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
13:59:57.952 [main] INFO io.github.donggi.bean.Hello5Test2 - null
13:59:57.952 [main] INFO io.github.donggi.bean.Hello5Test2 - [List 메시지 1, List 메시지 2]
13:59:57.952 [main] INFO io.github.donggi.bean.Hello5Test2 - [Set 메시지 1, Set 메시지 2]
map = 
{
    key1 = value2
    key2 = value2
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
