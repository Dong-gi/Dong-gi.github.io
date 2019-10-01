package io.github.donggi.controller;

import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import io.github.donggi.request.LoginRequest;

@Controller
@RequestMapping("/login.do")
public class LoginController {
    
    @RequestMapping(method = RequestMethod.GET)
    public String login() {
        return "login";
    }
    
    @RequestMapping(method = RequestMethod.POST)
    public ModelAndView login(LoginRequest loginRequest) {
       var mv = new ModelAndView();
       mv.setViewName("hello");
       mv.addObject("message", "환영합니다 : " + loginRequest.getEmail());
       return mv;
    }
    
}
