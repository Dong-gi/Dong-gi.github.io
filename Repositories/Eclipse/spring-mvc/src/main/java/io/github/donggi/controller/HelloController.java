package io.github.donggi.controller;

import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class HelloController {
    
    @Autowired
    private String commonMessage;

    @RequestMapping("/hello.do")
    public ModelAndView hello() {
       var mv = new ModelAndView();
       mv.setViewName("hello");
       mv.addObject("message", "안녕 세상~!@" + new Date());
       return mv;
    }
    
    @RequestMapping("/simple-hello.do")
    public String simpleHello() {
        return "안녕~!";
    }
    
    @RequestMapping(value = "/common-hello.do", method = RequestMethod.GET)
    public ModelAndView commonHello(HttpServletRequest request) {
       var mv = new ModelAndView();
       mv.setViewName("hello");
       mv.addObject("message", commonMessage + "@" + request.getRequestURI());
       return mv;
    }
    
}
