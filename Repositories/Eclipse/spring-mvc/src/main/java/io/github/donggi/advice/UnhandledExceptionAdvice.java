package io.github.donggi.advice;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice("io.github.donggi.controller")
public class UnhandledExceptionAdvice {
    @ExceptionHandler(Exception.class)
    public void handle(Exception e)
    {
        // Do Something...
    }
}
