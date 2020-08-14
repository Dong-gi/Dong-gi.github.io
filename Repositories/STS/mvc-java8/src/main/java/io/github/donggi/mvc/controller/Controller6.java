package io.github.donggi.mvc.controller;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
public class Controller6 {
    @RequestMapping("/6/from1/{msg}")
    public ModelAndView from1() {
        var mv = new ModelAndView("redirect:../to/{msg}");
        mv.addObject("num", 123);
        return mv;
    }
    @RequestMapping("/6/from2/{msg}")
    public View from2(Model m) {
        m.addAttribute("num", 123);
        return new RedirectView("../to/{msg}");
    }
    @RequestMapping("/6/from3/{msg}")
    public View from3(Model m, RedirectAttributes attrs) {
        m.addAttribute("num1", 123);
        attrs.addAttribute("num2", 456);
        return new RedirectView("../to/{msg}");
    }
    @RequestMapping("/6/to/{msg}")
    @ResponseStatus(HttpStatus.OK)
    public void to(HttpServletRequest request, @PathVariable String msg) {
        log.debug("uri : " + request.getRequestURI());
        log.debug("query : " + request.getQueryString());
        log.debug("msg : " + msg);
    }
/*
21:12:27.957 [http-nio-8080-exec-4] TRACE o.s.web.servlet.DispatcherServlet - GET "/mvc-java8/app/6/from1/Hello%20World", parameters={}, headers={masked} in DispatcherServlet 'app'
21:12:28.014 [http-nio-8080-exec-6] TRACE o.s.web.servlet.DispatcherServlet - GET "/mvc-java8/app/6/to/Hello%20World?num=123", parameters={masked}, headers={masked} in DispatcherServlet 'app'
21:12:28.049 [http-nio-8080-exec-6] DEBUG i.g.d.mvc.controller.Controller6 - uri : /mvc-java8/app/6/to/Hello%20World
21:12:28.049 [http-nio-8080-exec-6] DEBUG i.g.d.mvc.controller.Controller6 - query : num=123
21:12:28.049 [http-nio-8080-exec-6] DEBUG i.g.d.mvc.controller.Controller6 - msg : Hello World

21:12:31.355 [http-nio-8080-exec-1] TRACE o.s.web.servlet.DispatcherServlet - GET "/mvc-java8/app/6/from2/Hello%20World", parameters={}, headers={masked} in DispatcherServlet 'app'
21:12:31.367 [http-nio-8080-exec-9] TRACE o.s.web.servlet.DispatcherServlet - GET "/mvc-java8/app/6/to/Hello%20World?num=123", parameters={masked}, headers={masked} in DispatcherServlet 'app'
21:12:31.367 [http-nio-8080-exec-9] DEBUG i.g.d.mvc.controller.Controller6 - uri : /mvc-java8/app/6/to/Hello%20World
21:12:31.368 [http-nio-8080-exec-9] DEBUG i.g.d.mvc.controller.Controller6 - query : num=123
21:12:31.368 [http-nio-8080-exec-9] DEBUG i.g.d.mvc.controller.Controller6 - msg : Hello World

21:12:36.196 [http-nio-8080-exec-8] TRACE o.s.web.servlet.DispatcherServlet - GET "/mvc-java8/app/6/from3/Hello%20World", parameters={}, headers={masked} in DispatcherServlet 'app'
21:12:36.210 [http-nio-8080-exec-5] TRACE o.s.web.servlet.DispatcherServlet - GET "/mvc-java8/app/6/to/Hello%20World?num2=456", parameters={masked}, headers={masked} in DispatcherServlet 'app'
21:12:36.211 [http-nio-8080-exec-5] DEBUG i.g.d.mvc.controller.Controller6 - uri : /mvc-java8/app/6/to/Hello%20World
21:12:36.211 [http-nio-8080-exec-5] DEBUG i.g.d.mvc.controller.Controller6 - query : num2=456
21:12:36.211 [http-nio-8080-exec-5] DEBUG i.g.d.mvc.controller.Controller6 - msg : Hello World
 */
}