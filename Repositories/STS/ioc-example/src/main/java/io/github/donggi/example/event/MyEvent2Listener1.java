package io.github.donggi.example.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MyEvent2Listener1 {

    @EventListener
    public void onMyEvent2(MyEvent2 event) {
        log.info(event.getMsg());
    }

}
