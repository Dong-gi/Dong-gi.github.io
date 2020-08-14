package io.github.donggi.mvc.controller;

import java.util.Arrays;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
public class Controller {
    @GetMapping("/request1")
    @ResponseStatus(HttpStatus.OK)
    public void request1(HttpServletRequest request, @RequestParam String param) {
        log.debug("uri : " + request.getRequestURI());
        log.debug("query : " + request.getQueryString());
        log.debug("param : " + param);
/*
22:02:03.884 [http-nio-8080-exec-3] DEBUG i.g.donggi.mvc.controller.Controller - uri : /mvc-java8/app/request1
22:02:03.885 [http-nio-8080-exec-3] DEBUG i.g.donggi.mvc.controller.Controller - query : param=val
22:02:03.885 [http-nio-8080-exec-3] DEBUG i.g.donggi.mvc.controller.Controller - param : val
 */
    }

    @GetMapping("/request2")
    @ResponseStatus(HttpStatus.OK)
    public void request2(HttpServletRequest request, @RequestParam String[] param) {
        log.debug("uri : " + request.getRequestURI());
        log.debug("query : " + request.getQueryString());
        log.debug("param : " + Arrays.toString(param));
/*
22:03:10.631 [http-nio-8080-exec-17] DEBUG i.g.donggi.mvc.controller.Controller - uri : /mvc-java8/app/request2
22:03:10.631 [http-nio-8080-exec-17] DEBUG i.g.donggi.mvc.controller.Controller - query : param=val1&param=val2
22:03:10.631 [http-nio-8080-exec-17] DEBUG i.g.donggi.mvc.controller.Controller - param : [val1, val2]
 */
    }

    @GetMapping("/request3")
    @ResponseStatus(HttpStatus.OK)
    public void request3(HttpServletRequest request, @RequestParam Map<String, String> param) {
        log.debug("uri : " + request.getRequestURI());
        log.debug("query : " + request.getQueryString());
        log.debug("param : " + param);
/*
22:03:29.761 [http-nio-8080-exec-15] DEBUG i.g.donggi.mvc.controller.Controller - uri : /mvc-java8/app/request3
22:03:29.761 [http-nio-8080-exec-15] DEBUG i.g.donggi.mvc.controller.Controller - query : key1=val1&key2=val2&key1=val3
22:03:29.761 [http-nio-8080-exec-15] DEBUG i.g.donggi.mvc.controller.Controller - param : {key1=val1, key2=val2}
 */
    }

    @GetMapping("/request4")
    @ResponseStatus(HttpStatus.OK)
    public void request4(HttpServletRequest request, @RequestParam MultiValueMap<String, String> param) {
        log.debug("uri : " + request.getRequestURI());
        log.debug("query : " + request.getQueryString());
        log.debug("param : " + param);
/*
22:03:41.475 [http-nio-8080-exec-14] DEBUG i.g.donggi.mvc.controller.Controller - uri : /mvc-java8/app/request4
22:03:41.475 [http-nio-8080-exec-14] DEBUG i.g.donggi.mvc.controller.Controller - query : key1=val1&key2=val2&key1=val3
22:03:41.475 [http-nio-8080-exec-14] DEBUG i.g.donggi.mvc.controller.Controller - param : {key1=[val1, val3], key2=[val2]}
 */
    }
    
    @PostMapping("/request5")
    @ResponseStatus(HttpStatus.OK)
    public void request5(Request request) {
        log.debug(request.toString());
/*
10:23:44.141 [http-nio-8080-exec-8] DEBUG o.s.web.servlet.DispatcherServlet - POST "/mvc-java8/app/request5?text=%EC%95%88%EB%85%95%20%EC%84%B8%EC%83%81!&num=123", parameters={masked}
10:23:44.146 [http-nio-8080-exec-8] DEBUG o.s.w.s.m.m.a.RequestMappingHandlerMapping - Mapped to io.github.donggi.mvc.controller.Controller#request5(Request)
10:23:44.217 [http-nio-8080-exec-8] DEBUG i.g.donggi.mvc.controller.Controller - Request(text=안녕 세상!, num=123)
 */
    }
    
    @PostMapping("/request6")
    @ResponseStatus(HttpStatus.OK)
    public void request6(@RequestBody Request request) {
        log.debug(request.toString());
/*
10:29:49.003 [http-nio-8080-exec-14] DEBUG o.s.web.servlet.DispatcherServlet - POST "/mvc-java8/app/request6", parameters={}
10:29:49.004 [http-nio-8080-exec-14] DEBUG o.s.w.s.m.m.a.RequestMappingHandlerMapping - Mapped to io.github.donggi.mvc.controller.Controller#request6(Request)
10:29:49.175 [http-nio-8080-exec-14] DEBUG o.s.w.s.m.m.a.RequestResponseBodyMethodProcessor - Read "application/json" to [Request(text=안녕 세상!, num=123)]
10:29:49.176 [http-nio-8080-exec-14] DEBUG i.g.donggi.mvc.controller.Controller - Request(text=안녕 세상!, num=123)
 */
    }
}

@Data
class Request {
    private String text;
    private Integer num;
}