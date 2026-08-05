package io.github.donggi.anno;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Anno05Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/anno02.xml")) {
            for (var name : context.getBeanNamesForType(Anno05.class)) {
                var anno05 = (Anno05)context.getBean(name);
                log.infof("{} bean's set = {}", name, Arrays.toString(anno05.getAnno05Set().toArray()));
            }
        }
    }
/*
11:39:53.792 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'anno05'
11:39:53.794 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'anno05Config'
11:39:53.807 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'anno05C1'
11:39:53.809 [main] DEBUG org.springframework.beans.factory.support.DefaultListableBeanFactory - Creating shared instance of singleton bean 'anno05C2'
11:39:53.842 [main] INFO io.github.donggi.anno.Anno05Test - anno05 bean's set = [Anno05C1(message=@9809820416100), Anno05C2(message=@9809821511000)]
11:39:53.842 [main] INFO io.github.donggi.anno.Anno05Test - anno05C1 bean's set = [Anno05(message=@9809814609000), Anno05C2(message=@9809821511000)]
11:39:53.843 [main] INFO io.github.donggi.anno.Anno05Test - anno05C2 bean's set = [Anno05(message=@9809814609000), Anno05C1(message=@9809820416100)]
*/
}
