package io.github.donggi.mvc.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
public class Controller5 {
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class C5Request {
        private Long userId;
        private String param;
    }
    @RequestMapping("/5/test")
    @ResponseStatus(HttpStatus.OK)
    public void test(@RequestAttribute C5Request request) {
        log.debug(request.toString());
/*
20:27:28.219 [http-nio-8080-exec-2] DEBUG o.s.web.servlet.DispatcherServlet - GET "/mvc-java8/app/5/test", parameters={}
20:27:28.278 [http-nio-8080-exec-2] DEBUG i.g.d.mvc.controller.Controller5 - Controller5.C5Request(userId=123, param=value)
 */
    }
}