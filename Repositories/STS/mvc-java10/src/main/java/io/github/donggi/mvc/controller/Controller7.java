package io.github.donggi.mvc.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
public class Controller7 {
    @RequestMapping(value = "/7/get1")
    public StreamingResponseBody get1() {
        return (o) -> {
            for (var i = 0; i < 10; ++i) {
                try {
                    o.write(("Hello " + i).getBytes());
                    Thread.sleep(50);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}