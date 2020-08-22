package io.github.donggi.mvc.controller;

import java.util.Locale;
import java.util.stream.Collectors;

import javax.validation.Valid;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;

import io.github.donggi.mvc.request.LoginRequest;
import io.github.donggi.mvc.validator.LoginRequestValidator;

@Controller
public class Login implements ApplicationContextAware {
    private ApplicationContext context;

    @InitBinder
    protected void initBinder(WebDataBinder binder) {
        binder.setValidator(new LoginRequestValidator());
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
    @PostMapping("/login")
    public ModelAndView login(@Valid LoginRequest loginRequest, BindingResult result) {
        var mv = new ModelAndView("hello");
        if(result.hasErrors())
            mv.addObject("message", result.getAllErrors().stream().map(x -> context.getMessage(x, Locale.KOREAN)).collect(Collectors.joining(",")));
        else
            mv.addObject("message", "환영합니다 : " + loginRequest.getEmail());
        return mv;
    }
}