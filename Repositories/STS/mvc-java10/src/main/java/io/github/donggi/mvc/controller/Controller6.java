package io.github.donggi.mvc.controller;

import java.util.concurrent.Executors;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class Controller6 {
    @RequestMapping(value = "/6/get1", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter get1() {
        var r = new SseEmitter();
        Executors.newSingleThreadExecutor().execute(() -> {
            for (var i = 0; i < 10; ++i) {
                try {
                    r.send("Hello " + i);
                    Thread.sleep(50);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            r.complete();
        });
        return r;
    }
}