package io.github.donggi.mvc.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;




@RestController         // 18 고정
@Slf4j
public class Controller2 {
    @RequestMapping("/request01")
    @ResponseStatus(HttpStatus.OK)
    public void request1(@RequestHeader("Accept") String accept, @RequestHeader HttpHeaders headers) {
        log.debug("accept : " + accept);
        log.debug("headers : " + headers);
// 09:46:18.832 [http-nio-8080-exec-6] DEBUG i.g.d.mvc.controller.Controller2 - accept : */*
// 09:46:18.834 [http-nio-8080-exec-6] DEBUG i.g.d.mvc.controller.Controller2 - headers : [content-type:"application/json", user-agent:"PostmanRuntime/7.26.3", accept:"*/*", postman-token:"ad99d332-74be-49e5-90ec-1d6e53ddc4b0", host:"localhost:8080", accept-encoding:"gzip, deflate, br", connection:"keep-alive", content-length:"35"]
    }

    @Data
    static class GetListRequest {
        private Long userId;
        private String param;
    }
    @GetMapping("/request02/user/{userId}/param/{param}")
    @ResponseStatus(HttpStatus.OK)
    public void request2(@ModelAttribute GetListRequest request) {
        log.debug(request.toString());
/*
10:38:52.896 [http-nio-8080-exec-8] DEBUG o.s.web.servlet.DispatcherServlet - GET "/mvc-java8/app/request02/user/1234567890/param/something", parameters={}
10:38:52.900 [http-nio-8080-exec-8] DEBUG o.s.w.s.m.m.a.RequestMappingHandlerMapping - Mapped to io.github.donggi.mvc.controller.Controller2#request2(GetListRequest)
10:38:52.974 [http-nio-8080-exec-8] DEBUG i.g.d.mvc.controller.Controller2 - Controller2.GetListRequest(userId=1234567890, param=something)
 */
    }
    @GetMapping("/request03/user/{userId}/param/{param}")
    @ResponseStatus(HttpStatus.OK)
    public void request3(GetListRequest request) {
        log.debug(request.toString());
/*
10:39:54.716 [http-nio-8080-exec-4] DEBUG o.s.web.servlet.DispatcherServlet - GET "/mvc-java8/app/request03/user/1234567890/param/something", parameters={}
10:39:54.716 [http-nio-8080-exec-4] DEBUG o.s.w.s.m.m.a.RequestMappingHandlerMapping - Mapped to io.github.donggi.mvc.controller.Controller2#request3(GetListRequest)
10:39:54.735 [http-nio-8080-exec-4] DEBUG i.g.d.mvc.controller.Controller2 - Controller2.GetListRequest(userId=1234567890, param=something)
 */
    }
}