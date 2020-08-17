package io.github.donggi.mvc.controller;

import java.util.concurrent.Callable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller2 {
    @RequestMapping("/2/get1")
    public Callable<String> get1() {
        return () -> {
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return "Hello World";
        };
    }
/*
14:05:35.859 [http-nio-8080-exec-1] DEBUG o.s.web.servlet.DispatcherServlet - GET "/mvc-java10/app/2/get1", parameters={}
14:05:35.860 [http-nio-8080-exec-1] DEBUG o.s.w.s.m.m.a.RequestMappingHandlerMapping - Mapped to io.github.donggi.mvc.controller.Controller2#get1()
14:05:35.871 [http-nio-8080-exec-1] DEBUG i.g.d.m.i.CallableInterceptor - beforeConcurrentHandling
14:05:35.871 [http-nio-8080-exec-1] DEBUG o.s.w.c.r.async.WebAsyncManager - Started async request
14:05:35.872 [http-nio-8080-exec-1] DEBUG o.s.web.servlet.DispatcherServlet - Exiting but response remains open for further handling
14:05:36.372 [MvcAsync1] DEBUG o.s.w.c.r.async.WebAsyncManager - Async result set, dispatch to /mvc-java10/app/2/get1
14:05:36.375 [http-nio-8080-exec-2] DEBUG o.s.web.servlet.DispatcherServlet - Exiting from "ASYNC" dispatch, status 200
14:05:36.375 [http-nio-8080-exec-2] DEBUG i.g.d.m.i.CallableInterceptor - afterCompletion
 */
    @RequestMapping("/2/get2")
    public Callable<String> get2() {
        return () -> {
            try {
                Thread.sleep(2500L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return "Hello World";
        };
    }
/*
14:06:52.598 [http-nio-8080-exec-5] DEBUG o.s.web.servlet.DispatcherServlet - GET "/mvc-java10/app/2/get2", parameters={}
14:06:52.598 [http-nio-8080-exec-5] DEBUG o.s.w.s.m.m.a.RequestMappingHandlerMapping - Mapped to io.github.donggi.mvc.controller.Controller2#get2()
14:06:52.602 [http-nio-8080-exec-5] DEBUG i.g.d.m.i.CallableInterceptor - beforeConcurrentHandling
14:06:52.603 [http-nio-8080-exec-5] DEBUG o.s.w.c.r.async.WebAsyncManager - Started async request
14:06:52.603 [http-nio-8080-exec-5] DEBUG o.s.web.servlet.DispatcherServlet - Exiting but response remains open for further handling
14:06:54.280 [http-nio-8080-exec-6] DEBUG o.s.w.c.r.async.WebAsyncManager - Async request timeout for /mvc-java10/app/2/get2
14:06:54.280 [http-nio-8080-exec-6] DEBUG i.g.d.m.i.CallableInterceptor - handleTimeout
14:06:54.280 [http-nio-8080-exec-6] DEBUG o.s.w.c.r.async.WebAsyncManager - Async result set, dispatch to /mvc-java10/app/2/get2
14:06:54.283 [http-nio-8080-exec-6] DEBUG o.s.web.servlet.DispatcherServlet - Exiting from "ASYNC" dispatch, status 200
14:06:54.283 [http-nio-8080-exec-6] DEBUG i.g.d.m.i.CallableInterceptor - afterCompletion
 */
}