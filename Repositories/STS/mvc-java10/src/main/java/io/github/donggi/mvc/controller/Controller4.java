package io.github.donggi.mvc.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.WebAsyncTask;

@RestController
public class Controller4 {
    @RequestMapping("/4/get1")
    public WebAsyncTask<String> get1() {
        var t = new WebAsyncTask<String>(() -> {
            try {
                Thread.sleep(2500L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return "Hello World";
        });
        t.onTimeout(() -> "Timeout@onTimeout");
        return t;
    }
}