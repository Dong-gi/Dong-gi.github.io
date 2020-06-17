package io.github.donggi.bean;

import org.springframework.context.Lifecycle;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class Hello29 implements Lifecycle {
    private String message;

    @Override
    public void start() {
        if (isRunning()) return;
        message = "Started@" + System.currentTimeMillis();
        log.info("Hello29 Bean Started...");
    }

    @Override
    public void stop() {
        message = null;
        log.info("Hello29 Bean Stopped...");
    }

    @Override
    public boolean isRunning() {
        var result = message != null; 
        log.info("Hello29 Bean isRunning? " + result);
        return result;
    }

}
