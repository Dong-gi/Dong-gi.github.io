package io.github.donggi.bean;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class Hello1Test {

    @Test
    public void test() {
        System.out.println(new File("").getAbsolutePath());
        ApplicationContext context = new FileSystemXmlApplicationContext("src/main/resource/Beans1.xml");
        ((AbstractApplicationContext) context).registerShutdownHook();
        var message = ((Hello1) context.getBean("hello1")).getMessage();
        System.out.println(message);
        assertTrue(message != null);
    }

}
