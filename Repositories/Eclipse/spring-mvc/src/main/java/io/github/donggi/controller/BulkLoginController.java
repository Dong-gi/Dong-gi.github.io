package io.github.donggi.controller;

import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import io.github.donggi.request.BulkLoginRequest;

@Controller
@RequestMapping("/bulkLogin.do")
public class BulkLoginController {

    @RequestMapping(method = RequestMethod.GET)
    public String login() {
        return "bulkLogin";
    }

    @RequestMapping(method = RequestMethod.POST)
    public ModelAndView login(BulkLoginRequest loginRequest) {
        var mv = new ModelAndView();
        mv.setViewName("hello");
        mv.addObject("message", "환영합니다 : " +
                loginRequest.getLoginRequests().stream().map(x -> x.getEmail()).collect(Collectors.joining(", ")));
        return mv;
    }

}
