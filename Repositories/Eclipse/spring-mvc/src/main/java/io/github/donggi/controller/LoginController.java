package io.github.donggi.controller;

import java.util.stream.Collectors;

import javax.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import io.github.donggi.request.LoginRequest;
import io.github.donggi.validator.LoginRequestValidator;

@Controller
@RequestMapping("/login")
public class LoginController {

    @RequestMapping(method = RequestMethod.GET)
    public String login() {
        return "login";
    }

    @RequestMapping(method = RequestMethod.POST)
    public ModelAndView login(@Valid LoginRequest loginRequest, BindingResult result) {
        var mv = new ModelAndView();
        mv.setViewName("hello");
        if(result.hasErrors())
            mv.addObject("message", result.getAllErrors().stream().map(x -> x.getCode()).collect(Collectors.joining(",")));
        else
            mv.addObject("message", "환영합니다 : " + loginRequest.getEmail());
        return mv;
    }

    @InitBinder
    protected void initBinder(WebDataBinder binder) {
        binder.setValidator(new LoginRequestValidator());
    }

}
