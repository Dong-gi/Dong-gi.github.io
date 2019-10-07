package io.github.donggi.controller;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.stream.Collectors;

import javax.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import io.github.donggi.request.LoginRequest;
import io.github.donggi.validator.LoginRequestValidator;

@Controller
@RequestMapping("/etc/*")
public class EtcController {

    @RequestMapping(value = "/date", method = RequestMethod.GET)
    public String login() {
        return "etc/date";
    }

    @RequestMapping(value = "/date", method = RequestMethod.POST)
    public ModelAndView login(Date date1, Date date2) {
        var mv = new ModelAndView();
        mv.setViewName("hello");
        var format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        mv.addObject("message", String.format("date1 : %s, date2 : %s", format.format(date1), format.format(date2)));
        return mv;
    }
    
    @RequestMapping(value = "/json", method = RequestMethod.GET)
    public String json() {
        return "etc/json";
    }

    @ResponseBody
    @RequestMapping(value = "/json", method = RequestMethod.POST)
    public String json(@RequestBody String body) {
        return String.format("{\"status\":200,\"echo\":\"%s\"}", body);
    }
    
}
