package io.github.donggi.bean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.collections4.MapUtils;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class Beans19Test {

    @Test
    public void test() {
        ApplicationContext context = new FileSystemXmlApplicationContext("src/main/resource/Beans19.xml");
        ((AbstractApplicationContext) context).registerShutdownHook();
        var list1 = (List<String>) context.getBean("list1");
        var list2 = (List<String>) context.getBean("list2");
        System.out.println(list1.size());
        System.out.println(list2.size());
        System.out.println(list2.getClass());
        System.out.println(Arrays.toString(list1.toArray()));
        System.out.println(Arrays.toString(list2.toArray()));
    }
/*
4
0
class $java.util.ArrayList$$EnhancerBySpringCGLIB$$4c577b42
[메시지 1, 메시지 1, , 메시지 2]
[메시지 1, 메시지 1, , 메시지 2]
 */
}
