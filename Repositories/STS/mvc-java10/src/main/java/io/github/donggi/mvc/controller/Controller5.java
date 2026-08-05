package io.github.donggi.mvc.controller;

import java.util.concurrent.Executors;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

@RestController
public class Controller5 {
    @RequestMapping("/5/get1")
    public ResponseBodyEmitter get1() {
        var r = new ResponseBodyEmitter();
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