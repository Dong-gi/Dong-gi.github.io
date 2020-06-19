package io.github.donggi.example.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
class Event1Tests {

    @Autowired
    private MyEvent1Publisher publisher;
    
    @Test
    void test() {
        log.info("1");
        publisher.publish("Hello");
        log.info("2");
        publisher.publish("World");
        log.info("By default, event listener is synchronous with publisher");
    }
/*
14:23:36.648 [main] INFO  i.g.donggi.example.event.Event1Tests - 1
14:23:36.649 [main] INFO  i.g.d.e.event.MyEvent1Listener1 - Hello
14:23:36.653 [main] INFO  i.g.donggi.example.event.Event1Tests - 2
14:23:36.654 [main] INFO  i.g.d.e.event.MyEvent1Listener1 - World
14:23:36.654 [main] INFO  i.g.donggi.example.event.Event1Tests - By default, event listener is synchronous with publisher
14:23:36.752 [task-1] INFO  i.g.d.e.event.MyEvent1Listener2 - Hello
14:23:36.752 [task-2] INFO  i.g.d.e.event.MyEvent1Listener2 - World
*/
}
