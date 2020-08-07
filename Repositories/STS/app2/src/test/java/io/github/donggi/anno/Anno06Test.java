package io.github.donggi.anno;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Anno06Test {
    @Test
    public void test() {
        try (var context = new FileSystemXmlApplicationContext("src/main/resource/anno02.xml")) {
            for (var name : context.getBeanNamesForType(Anno06.class)) {
                var anno06 = (Anno06)context.getBean(name);
                log.infof("↓ Bean {}'s map", name);
                anno06.getAnno06Map().forEach((key, value) -> log.infof("key={}, value={}", key, value));
            }
        }
    }
/*
11:44:10.060 [main] INFO io.github.donggi.anno.Anno06Test - ↓ Bean anno06's map
11:44:10.061 [main] INFO io.github.donggi.anno.Anno06Test - key=anno06C1, value=Anno06C1(message=@10066048934100)
11:44:10.061 [main] INFO io.github.donggi.anno.Anno06Test - key=anno06C2, value=Anno06C2(message=@10066050072700)
11:44:10.061 [main] INFO io.github.donggi.anno.Anno06Test - ↓ Bean anno06C1's map
11:44:10.061 [main] INFO io.github.donggi.anno.Anno06Test - key=anno06, value=Anno06(message=@10066044856600)
11:44:10.062 [main] INFO io.github.donggi.anno.Anno06Test - key=anno06C2, value=Anno06C2(message=@10066050072700)
11:44:10.062 [main] INFO io.github.donggi.anno.Anno06Test - ↓ Bean anno06C2's map
11:44:10.062 [main] INFO io.github.donggi.anno.Anno06Test - key=anno06, value=Anno06(message=@10066044856600)
11:44:10.063 [main] INFO io.github.donggi.anno.Anno06Test - key=anno06C1, value=Anno06C1(message=@10066048934100)
*/
}
