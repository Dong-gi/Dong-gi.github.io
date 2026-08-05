package io.github.donggi.example.event;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MyEvent2Listener2 {

    @EventListener
    public void onEvent(Object e) {
        log.info("Something({}) happened...", e);
    }

    @Async
    @EventListener({ MyEvent2.class })
    public void onMyEvent2() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {}
        log.info("MyEvent2 happened...");
    }

}
