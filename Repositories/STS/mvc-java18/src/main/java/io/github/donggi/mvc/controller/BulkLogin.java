package io.github.donggi.mvc.controller;

import java.util.stream.Collectors;

import javax.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;

import io.github.donggi.mvc.request.BulkLoginRequest;

@Controller
public class BulkLogin {
    @GetMapping("/bulkLogin")
    public String login() {
        return "bulkLogin";
    }
    @PostMapping("/bulkLogin")
    public ModelAndView login(@Valid BulkLoginRequest loginRequest, BindingResult result) {
        var mv = new ModelAndView("hello");
        mv.addObject("message", "환영합니다 : " + loginRequest.getLoginRequests().stream().map(x -> x.getEmail()).collect(Collectors.joining(", ")));
        return mv;
    }
/*
org.springframework.validation.BeanPropertyBindingResult: 3 errors
Field error in object 'bulkLoginRequest' on field 'loginRequests[1].password': rejected value [password]; codes [Size.bulkLoginRequest.loginRequests[1].password,Size.bulkLoginRequest.loginRequests.password,Size.loginRequests[1].password,Size.loginRequests.password,Size.password,Size.java.lang.String,Size]; arguments [org.springframework.context.support.DefaultMessageSourceResolvable: codes [bulkLoginRequest.loginRequests[1].password,loginRequests[1].password]; arguments []; default message [loginRequests[1].password],3,0]; default message [size must be between 0 and 3]
Field error in object 'bulkLoginRequest' on field 'loginRequests[2].password': rejected value [password]; codes [Size.bulkLoginRequest.loginRequests[2].password,Size.bulkLoginRequest.loginRequests.password,Size.loginRequests[2].password,Size.loginRequests.password,Size.password,Size.java.lang.String,Size]; arguments [org.springframework.context.support.DefaultMessageSourceResolvable: codes [bulkLoginRequest.loginRequests[2].password,loginRequests[2].password]; arguments []; default message [loginRequests[2].password],3,0]; default message [size must be between 0 and 3]
Field error in object 'bulkLoginRequest' on field 'loginRequests[0].password': rejected value [password]; codes [Size.bulkLoginRequest.loginRequests[0].password,Size.bulkLoginRequest.loginRequests.password,Size.loginRequests[0].password,Size.loginRequests.password,Size.password,Size.java.lang.String,Size]; arguments [org.springframework.context.support.DefaultMessageSourceResolvable: codes [bulkLoginRequest.loginRequests[0].password,loginRequests[0].password]; arguments []; default message [loginRequests[0].password],3,0]; default message [size must be between 0 and 3]
 */
}
