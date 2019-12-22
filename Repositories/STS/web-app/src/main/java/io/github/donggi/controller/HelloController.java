package io.github.donggi.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.donggi.response.HelloResponse;

@RestController
public class HelloController {
    @Autowired
    private SleepController sleepController;

    @RequestMapping("/hello")
    public HelloResponse hello() {
        // sleepController  SleepController$$EnhancerBySpringCGLIB$$429837e0  (id=139)
        sleepController.justSleep();
        return new HelloResponse("안녕 세상!");
    }
    
    @Scheduled(fixedDelay = 1000)
    public void scheduledAction() {
        System.out.println(System.currentTimeMillis());
    }
}
