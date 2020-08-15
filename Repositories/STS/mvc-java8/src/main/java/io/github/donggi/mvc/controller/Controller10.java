package io.github.donggi.mvc.controller;

import java.util.Date;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class Controller10 {
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping("/10/date1/{dateText}")
    public void date1(@PathVariable(name = "dateText") Date date) {
        log.debug("Date : " + date);
    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping("/10/date2")
    public void date2(Date date) {
        log.debug("Date : " + date);
    }
/*
16:19:34.058 [http-nio-8080-exec-2] TRACE o.s.web.servlet.DispatcherServlet - GET "/mvc-java8/app/10/date1/Y2000M02D22", parameters={}, headers={masked} in DispatcherServlet 'app'
16:19:34.065 [http-nio-8080-exec-2] TRACE o.s.w.s.m.m.a.RequestMappingHandlerMapping - Mapped to io.github.donggi.mvc.controller.Controller10#date1(Date)
16:19:34.121 [http-nio-8080-exec-2] DEBUG i.g.d.mvc.controller.Controller10 - Date : Tue Feb 22 00:00:00 KST 2000

16:19:53.934 [http-nio-8080-exec-6] TRACE o.s.web.servlet.DispatcherServlet - GET "/mvc-java8/app/10/date2?date=2020%EB%85%8402%EC%9B%9420%EC%9D%BC", parameters={masked}, headers={masked} in DispatcherServlet 'app'
16:19:53.935 [http-nio-8080-exec-6] TRACE o.s.w.s.m.m.a.RequestMappingHandlerMapping - Mapped to io.github.donggi.mvc.controller.Controller10#date2(Date)
16:19:53.951 [http-nio-8080-exec-6] DEBUG i.g.d.mvc.controller.Controller10 - Date : Thu Feb 20 00:00:00 KST 2020
 */
}