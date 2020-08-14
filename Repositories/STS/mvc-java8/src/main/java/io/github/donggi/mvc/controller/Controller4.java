package io.github.donggi.mvc.controller;

import javax.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
public class Controller4 {
    @Data
    static class C4Request {
        private Long userId;
        private String param;
    }
    @RequestMapping("/4/checkSession")
    @ResponseStatus(HttpStatus.OK)
    public void checkSession(@SessionAttribute(name = "c4Request", required = false) C4Request request) {
        if (request == null)
            log.debug("No session attribute 'C4Request'");
        else
            log.debug("Session attribute 'C4Request' : " + request.toString());
    }
    @RequestMapping("/4/setRequest/user/{userId}/param/{param}")
    @ResponseStatus(HttpStatus.OK)
    public void setRequest(HttpSession session, C4Request request) {
        session.setAttribute("c4Request", request);
        log.debug("Set session attribute 'C4Request' : " + request.toString());
    }
}
/*
13:14:53.427 [http-nio-8080-exec-16] DEBUG o.s.web.servlet.DispatcherServlet - GET "/mvc-java8/app/4/checkSession", parameters={}
13:14:53.490 [http-nio-8080-exec-16] DEBUG i.g.d.mvc.controller.Controller4 - No session attribute 'C4Request'

13:14:57.609 [http-nio-8080-exec-15] DEBUG o.s.web.servlet.DispatcherServlet - GET "/mvc-java8/app/4/setRequest/user/123/param/value", parameters={}
13:14:57.639 [http-nio-8080-exec-15] DEBUG i.g.d.mvc.controller.Controller4 - Set session attribute 'C4Request' : Controller4.C4Request(userId=123, param=value)

13:14:59.750 [http-nio-8080-exec-4] DEBUG o.s.web.servlet.DispatcherServlet - GET "/mvc-java8/app/4/checkSession", parameters={}
13:14:59.751 [http-nio-8080-exec-4] DEBUG i.g.d.mvc.controller.Controller4 - Session attribute 'C4Request' : Controller4.C4Request(userId=123, param=value)
*/