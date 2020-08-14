package io.github.donggi.mvc.controller;

import javax.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@SessionAttributes("c3Request")
public class Controller3 {
    @Data
    static class C3Request {
        private Long userId;
        private String param;
    }
    @RequestMapping("/3/checkSession")
    @ResponseStatus(HttpStatus.OK)
    public void checkSession(HttpSession session) {
        log.debug("session contains 'c3Request' : " + (session.getAttribute("c3Request") != null));
    }
    @RequestMapping("/3/setRequest/user/{userId}/param/{param}")
    @ResponseStatus(HttpStatus.OK)
    public void setRequest(C3Request request) {
        log.debug(request.toString());
    }
    @RequestMapping("/3/complete")
    @ResponseStatus(HttpStatus.OK)
    public void complete(C3Request request, SessionStatus status) {
        log.debug("request exists : " + request);
        status.setComplete();
    }
}
/*
11:53:25.396 [http-nio-8080-exec-3] DEBUG o.s.web.servlet.DispatcherServlet - GET "/mvc-java8/app/3/checkSession", parameters={}
11:53:25.435 [http-nio-8080-exec-3] DEBUG i.g.d.mvc.controller.Controller3 - session contains 'c3Request' : false

11:53:32.969 [http-nio-8080-exec-7] DEBUG o.s.web.servlet.DispatcherServlet - GET "/mvc-java8/app/3/setRequest/user/123/param/value", parameters={}
11:53:33.021 [http-nio-8080-exec-7] DEBUG i.g.d.mvc.controller.Controller3 - Controller3.C3Request(userId=123, param=value)

11:53:37.715 [http-nio-8080-exec-9] DEBUG o.s.web.servlet.DispatcherServlet - GET "/mvc-java8/app/3/checkSession", parameters={}
11:53:37.716 [http-nio-8080-exec-9] DEBUG i.g.d.mvc.controller.Controller3 - session contains 'c3Request' : true

11:54:06.692 [http-nio-8080-exec-8] DEBUG o.s.web.servlet.DispatcherServlet - GET "/mvc-java8/app/3/complete", parameters={}
11:54:06.693 [http-nio-8080-exec-8] DEBUG i.g.d.mvc.controller.Controller3 - request exists : Controller3.C3Request(userId=123, param=value)

11:54:15.954 [http-nio-8080-exec-11] DEBUG o.s.web.servlet.DispatcherServlet - GET "/mvc-java8/app/3/checkSession", parameters={}
11:54:15.954 [http-nio-8080-exec-11] DEBUG i.g.d.mvc.controller.Controller3 - session contains 'c3Request' : false
*/