package io.github.donggi.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.donggi.response.HelloResponse;
import reactor.core.publisher.Mono;

@RestController
public class HelloController {

    @RequestMapping("/hello")
    public Mono<HelloResponse> hello() {
        return Mono.just(new HelloResponse("안녕 세상!"));
    }
}
