package io.github.donggi.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.donggi.response.HelloResponse;

@RestController
public class HelloController {

    @RequestMapping("/hello")
    public HelloResponse hello() {
        return new HelloResponse("안녕 세상!");
    }
}
