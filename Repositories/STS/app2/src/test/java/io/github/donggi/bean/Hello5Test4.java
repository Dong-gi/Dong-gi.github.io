package io.github.donggi.bean;

import java.util.Arrays;

import org.apache.commons.collections4.MapUtils;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello5Test4 {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans31.xml")) {
            var hello5 = (Hello5) context.getBean("child");
            log.info(Arrays.toString(hello5.getMessageArr()));
            log.info(Arrays.toString(hello5.getMessageList().toArray()));
            log.info(Arrays.toString(hello5.getMessageSet().toArray()));
            MapUtils.verbosePrint(System.out, "map", hello5.getMessageMap());
            MapUtils.verbosePrint(System.out, "prop1", hello5.getMessageProps1());
            MapUtils.verbosePrint(System.out, "prop2", hello5.getMessageProps2());
        }
    }
/*
14:01:59.320 [main] INFO io.github.donggi.bean.Hello5Test4 - [Arr 메시지 1, null, Arr 메시지 2, New array element]
14:01:59.320 [main] INFO io.github.donggi.bean.Hello5Test4 - [List 메시지 1, List 메시지 2, New list element]
14:01:59.320 [main] INFO io.github.donggi.bean.Hello5Test4 - [Set 메시지 1, Set 메시지 2, Set 메시지 3, New set element]
map = 
{
    key1 = value1
    key2 = value2
    key3 = value3
}
prop1 = 
{
    key1 = value1
    key2 = value2
    key3 = value3
}
prop2 = 
{
    key1 = value1
    key2 = value1
}
*/
}
