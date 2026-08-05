package io.github.donggi.mvc.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class MyController {
    @RequestMapping("/hello")
    public ModelAndView get1() {
        var r = new ModelAndView("hello");
        r.addObject("name", "Donggi Kim");
        return r;
    }
}