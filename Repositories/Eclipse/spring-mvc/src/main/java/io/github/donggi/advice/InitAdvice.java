package io.github.donggi.advice;

import java.util.Date;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.context.request.WebRequest;

import io.github.donggi.util.MultipleDateEditor;

@ControllerAdvice("io.github.donggi.controller")
public class InitAdvice {

    @InitBinder
    public void registerCustomEditors(WebDataBinder binder, WebRequest request) {
        binder.registerCustomEditor(Date.class, MultipleDateEditor.of(false, "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"));
    }

}
