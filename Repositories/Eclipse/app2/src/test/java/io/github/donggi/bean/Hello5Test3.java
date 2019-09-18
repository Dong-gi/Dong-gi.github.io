package io.github.donggi.bean;

import java.io.File;
import java.util.Arrays;

import org.apache.commons.collections4.MapUtils;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class Hello5Test3 {

    @Test
    public void test() {
        System.out.println(new Integer[] {}.getClass());
        System.out.println(new File("").getAbsolutePath());
        ApplicationContext context = new FileSystemXmlApplicationContext("src/main/resource/Beans10.xml");
        ((AbstractApplicationContext) context).registerShutdownHook();
        var hello5 = (Hello5) context.getBean("hello5");
        System.out.println(Arrays.toString(hello5.getMessageArr()));
        System.out.println(Arrays.toString(hello5.getMessageList().toArray()));
        System.out.println(Arrays.toString(hello5.getMessageSet().toArray()));
        MapUtils.verbosePrint(System.out, null, hello5.getMessageMap());
        MapUtils.verbosePrint(System.out, null, hello5.getMessageProps());
    }
/*
22:06:10.357 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'hello5'
[메시지 1, 메시지 1, , 메시지 2]
[메시지 1, 메시지 1, , 메시지 2]
[, 메시지 1, 메시지 2]
{
    key1 = value1
    key2 = value2
}
{
    key1 = value1
    key2 = value2
}
 */
}
