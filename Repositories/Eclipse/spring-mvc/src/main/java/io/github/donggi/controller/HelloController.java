package io.github.donggi.controller;

import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/hello/*")
public class HelloController {

    @Autowired
    private String commonMessage;

    @RequestMapping("")
    public ModelAndView hello() {
        var mv = new ModelAndView();
        mv.setViewName("hello");
        mv.addObject("message", "안녕 세상~!@" + new Date());
        return mv;
    }

    @RequestMapping("/simpleHello")
    public String simpleHello() {
        return "hello";
    }

    @RequestMapping(value = "commonHello", method = RequestMethod.GET)
    public ModelAndView commonHello(HttpServletRequest request) {
        var mv = new ModelAndView();
        mv.setViewName("hello");
        mv.addObject("message", commonMessage + "@" + request.getRequestURI());
        return mv;
    }

    @RequestMapping("/{name}/**/{message}")
    public ModelAndView restHello(@PathVariable String name, @PathVariable String message) {
        var mv = new ModelAndView();
        mv.setViewName("hello");
        mv.addObject("message", message + "@" + name);
        return mv;
    }

    @RequestMapping("/fail")
    public void failHello() {
        ExceptionUtils.rethrow(new Exception("그냥 던져봤어@" + new Date()));
    }

    @RequestMapping("/fail2")
    public void failHello2() {
        ExceptionUtils.rethrow(new NullPointerException("그냥 던져봤어2@" + new Date()));
    }

    @ExceptionHandler(NullPointerException.class)
    public ModelAndView nullPointerExceptionHandler(NullPointerException e) {
        var mv = new ModelAndView();
        mv.setViewName("hello");
        mv.addObject("message", e.getMessage() + "@HelloContoroller::nullPointerExceptionHandler");
        return mv;
    }

}
