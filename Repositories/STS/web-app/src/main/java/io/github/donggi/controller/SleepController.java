package io.github.donggi.controller;

import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SleepController {

    @Async
    public void justSleep() {
        // 결과를 대기할 필요가 있다면 Future를 반환하면 된다
        // org.springframework.util.concurrent.ListenableFuture
        // java.util.concurrent.CompletableFuture
        try {
            System.out.println("Sleep start");
            Thread.sleep(5000);
            System.out.println("Sleep end");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
