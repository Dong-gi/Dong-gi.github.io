package io.github.donggi.bean;

import java.util.Arrays;

import org.apache.commons.collections4.MapUtils;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class Hello5Test {

    @Test
    public void test() {
        ApplicationContext context = new FileSystemXmlApplicationContext("src/main/resource/Beans8.xml");
        ((AbstractApplicationContext) context).registerShutdownHook();
        var hello5 = (Hello5) context.getBean("hello5");
        System.out.println(Arrays.toString(hello5.getMessageArr()));
        System.out.println(Arrays.toString(hello5.getMessageList().toArray()));
        System.out.println(Arrays.toString(hello5.getMessageSet().toArray()));
        MapUtils.verbosePrint(System.out, null, hello5.getMessageMap());
        MapUtils.verbosePrint(System.out, null, hello5.getMessageProps());
    }
/*
21:07:10.211 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'hello5'
[Arr 메시지 1, null, Arr 메시지 2]
[List 메시지 1, List 메시지 2]
[Set 메시지 1, Set 메시지 2, Set 메시지 3]
{
    key1 = value2
    key2 = value1
}
{
    key1 = value2
    key2 = value2
}
 */
}
