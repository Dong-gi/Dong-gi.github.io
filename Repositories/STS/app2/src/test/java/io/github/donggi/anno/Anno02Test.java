package io.github.donggi.anno;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Anno02Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/anno02.xml")) {
            for (var name : context.getBeanNamesForType(Anno02.class)) {
                var anno02 = (Anno02)context.getBean(name);
                log.infof("{} bean's arr = {}", name, Arrays.toString(anno02.getAnno02Arr()));
            }
        }
    }
/*
11:18:25.014 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'anno02'
11:18:25.062 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'anno02Config'
11:18:25.088 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'anno02C1'
11:18:25.093 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'anno02C2'
11:18:25.157 [main] INFO io.github.donggi.anno.Anno02Test - anno02 bean's arr = [Anno02C1(message=Default Message By Annotation@8521103740700), Anno02C2(message=Default Message By Annotation@8521105349699)]
11:18:25.158 [main] INFO io.github.donggi.anno.Anno02Test - anno02C1 bean's arr = [Anno02(message=Default Message By Annotation@8521094999499), Anno02C2(message=Default Message By Annotation@8521105349699)]
11:18:25.158 [main] INFO io.github.donggi.anno.Anno02Test - anno02C2 bean's arr = [Anno02(message=Default Message By Annotation@8521094999499), Anno02C1(message=Default Message By Annotation@8521103740700)]
*/
}
