package io.github.donggi.mvc.controller;

import java.util.concurrent.Executors;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

@RestController
public class Controller1 {
    @RequestMapping("/1/get1")
    public DeferredResult<String> get1() {
        final var r = new DeferredResult<String>();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Thread.sleep(500L);
                r.setResult("Hello World");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        return r;
    }
/*
14:02:35.243 [http-nio-8080-exec-2] DEBUG o.s.web.servlet.DispatcherServlet - GET "/mvc-java10/app/1/get1", parameters={}
14:02:35.250 [http-nio-8080-exec-2] DEBUG o.s.w.s.m.m.a.RequestMappingHandlerMapping - Mapped to io.github.donggi.mvc.controller.Controller1#get1()
14:02:35.280 [http-nio-8080-exec-2] DEBUG i.g.d.m.i.DeferredResultInterceptor - beforeConcurrentHandling
14:02:35.283 [http-nio-8080-exec-2] DEBUG o.s.w.c.r.async.WebAsyncManager - Started async request
14:02:35.285 [http-nio-8080-exec-2] DEBUG o.s.web.servlet.DispatcherServlet - Exiting but response remains open for further handling
14:02:35.774 [pool-1-thread-1] DEBUG o.s.w.c.r.async.WebAsyncManager - Async result set, dispatch to /mvc-java10/app/1/get1
14:02:35.813 [http-nio-8080-exec-3] DEBUG o.s.web.servlet.DispatcherServlet - Exiting from "ASYNC" dispatch, status 200
14:02:35.814 [http-nio-8080-exec-3] DEBUG i.g.d.m.i.DeferredResultInterceptor - afterCompletion
 */
    @RequestMapping("/1/get2")
    public DeferredResult<String> get2() {
        final var r = new DeferredResult<String>();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Thread.sleep(2500L);
                r.setResult("Hello World");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        return r;
    }
/*
14:04:29.903 [http-nio-8080-exec-7] DEBUG o.s.web.servlet.DispatcherServlet - GET "/mvc-java10/app/1/get2", parameters={}
14:04:29.903 [http-nio-8080-exec-7] DEBUG o.s.w.s.m.m.a.RequestMappingHandlerMapping - Mapped to io.github.donggi.mvc.controller.Controller1#get2()
14:04:29.909 [http-nio-8080-exec-7] DEBUG i.g.d.m.i.DeferredResultInterceptor - beforeConcurrentHandling
14:04:29.909 [http-nio-8080-exec-7] DEBUG o.s.w.c.r.async.WebAsyncManager - Started async request
14:04:29.909 [http-nio-8080-exec-7] DEBUG o.s.web.servlet.DispatcherServlet - Exiting but response remains open for further handling
14:04:31.281 [http-nio-8080-exec-8] DEBUG i.g.d.m.i.DeferredResultInterceptor - handleTimeout
14:04:31.282 [http-nio-8080-exec-8] DEBUG o.s.w.c.r.async.WebAsyncManager - Async result set, dispatch to /mvc-java10/app/1/get2
14:04:31.284 [http-nio-8080-exec-8] DEBUG o.s.web.servlet.DispatcherServlet - Exiting from "ASYNC" dispatch, status 200
14:04:31.284 [http-nio-8080-exec-8] DEBUG i.g.d.m.i.DeferredResultInterceptor - afterCompletion
 */
}