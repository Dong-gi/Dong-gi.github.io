package io.github.donggi.mvc.controller;

import java.util.concurrent.Executors;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

@RestController
public class Controller3 {
    @RequestMapping("/3/get1")
    public DeferredResult<String> get1() {
        final var r = new DeferredResult<String>();
        r.onTimeout(() -> r.setErrorResult("Timeout@onTimeout"));
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Thread.sleep(2500L);
                r.setResult("Hello World");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        return r;
    }
}