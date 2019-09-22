package io.github.donggi.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class HelloController {

    @RequestMapping("/hello.do")
    public ModelAndView hello() {
       var mv = new ModelAndView();
       mv.setViewName("hello");
       mv.addObject("message", "안녕 세상~!");
       return mv;
    }
}
