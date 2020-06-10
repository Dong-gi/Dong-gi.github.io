package io.github.donggi.bean;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello22Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/Beans36.xml");) {
            var hello22 = (Hello22) context.getBean("hello22");
            log.info(hello22.getClass());
            log.info(hello22.multipleMessage(10));
        }
    }
/*
17:15:45.043 [main] INFO io.github.donggi.bean.Hello22Test - class io.github.donggi.bean.Hello22$$EnhancerBySpringCGLIB$$1a449f80
17:15:45.043 [main] INFO io.github.donggi.bean.Hello22Replacement - (Hello22(message=Hello World)).multipleMessage([10])
17:15:45.044 [main] INFO io.github.donggi.bean.Hello22Test - Hello World * 10
*/
}
