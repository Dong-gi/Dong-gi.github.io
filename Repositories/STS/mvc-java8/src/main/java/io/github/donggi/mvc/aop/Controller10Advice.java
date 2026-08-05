package io.github.donggi.mvc.aop;

import java.util.Date;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

import io.github.donggi.mvc.controller.Controller10;
import io.github.donggi.mvc.util.MultiDateEditor;

@ControllerAdvice(assignableTypes = Controller10.class)
public class Controller10Advice {

    @InitBinder
    public void registerCustomEditors(WebDataBinder binder) {
        binder.registerCustomEditor(Date.class, MultiDateEditor.of(false, "'Y'yyyy'M'MM'D'dd", "yyyy년MM월dd일"));
    }

}
