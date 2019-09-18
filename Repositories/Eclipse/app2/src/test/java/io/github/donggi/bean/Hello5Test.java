package io.github.donggi.bean;

import java.io.File;
import java.util.Arrays;

import org.apache.commons.collections4.MapUtils;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class Hello5Test {

    @Test
    public void test() {
        System.out.println(new File("").getAbsolutePath());
        ApplicationContext context = new FileSystemXmlApplicationContext("src/main/resource/Beans8.xml");
        ((AbstractApplicationContext) context).registerShutdownHook();
        var hello5 = (Hello5) context.getBean("hello5");
        System.out.println(Arrays.toString(hello5.getMessageArr()));
        System.out.println(Arrays.toString(hello5.getMessageList().toArray()));
        System.out.println(Arrays.toString(hello5.getMessageSet().toArray()));
        MapUtils.verbosePrint(System.out, null, hello5.getMessageMap());
    }
/*
빈 메시지 1
빈 메시지 2
빈 메시지 1
빈 메시지 3
21:56:22.220 [Thread-0] DEBUG org.springframework.context.support.FileSystemXmlApplicationContext - Closing org.springframework.context.support.FileSystemXmlApplicationContext@6ad82709, started on Tue Sep 17 21:56:21 KST 2019
 */
}
