package io.github.donggi.mvc.controller;

import java.util.Date;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class Message {
    @GetMapping("/message")
    public ModelAndView message() {
        var mv = new ModelAndView("message");
        mv.addObject("now", new Date());
        return mv;
    }
}
