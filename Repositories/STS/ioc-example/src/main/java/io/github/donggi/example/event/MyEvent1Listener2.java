package io.github.donggi.example.event;

import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MyEvent1Listener2 implements ApplicationListener<MyEvent1> {

    @Override
    @Async
    public void onApplicationEvent(MyEvent1 event) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {}
        log.info(event.getMsg());
    }

}
