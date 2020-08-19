package io.github.donggi.mvc.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller1 {
    @ResponseBody
    @RequestMapping("/data")
    public String data() {
        return "{\"msg\":\"Hello World\"}";
    }
//09:11:19.247 [http-nio-8080-exec-2] TRACE o.s.web.servlet.DispatcherServlet - GET "/mvc-java15/app/data.xxx", parameters={}, headers={masked} in DispatcherServlet 'app'
//09:11:19.248 [http-nio-8080-exec-2] DEBUG o.s.w.s.m.m.a.RequestResponseBodyMethodProcessor - Using 'application/json', given [application/json] and supported [text/plain, */*, application/json, application/*+json]
//09:11:19.248 [http-nio-8080-exec-2] TRACE o.s.w.s.m.m.a.RequestResponseBodyMethodProcessor - Writing ["{"msg":"Hello World"}"]
//
//09:11:51.324 [http-nio-8080-exec-6] TRACE o.s.web.servlet.DispatcherServlet - GET "/mvc-java15/app/data.yyy", parameters={}, headers={masked} in DispatcherServlet 'app'
//09:11:51.325 [http-nio-8080-exec-6] DEBUG o.s.w.s.m.m.a.RequestResponseBodyMethodProcessor - Using 'text/html', given [text/html, application/xhtml+xml, image/webp, image/apng, application/xml;q=0.9, application/signed-exchange;v=b3;q=0.9, */*;q=0.8] and supported [text/plain, */*, application/json, application/*+json]
//09:11:51.325 [http-nio-8080-exec-6] TRACE o.s.w.s.m.m.a.RequestResponseBodyMethodProcessor - Writing ["{"msg":"Hello World"}"]
}