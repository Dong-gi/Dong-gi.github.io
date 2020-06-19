package io.github.donggi.example.event;

import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MyEvent1Listener1 implements ApplicationListener<MyEvent1> {

    @Override
    public void onApplicationEvent(MyEvent1 event) {
        log.info(event.getMsg());
    }

}
