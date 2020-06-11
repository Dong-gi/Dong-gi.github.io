package io.github.donggi.example.controller;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.donggi.example.entity.PerApp;
import io.github.donggi.example.entity.PerMinute;
import io.github.donggi.example.entity.PerRequest;
import io.github.donggi.example.entity.PerSession;
import io.github.donggi.example.response.CommonResponse;
import lombok.extern.slf4j.Slf4j;








@Slf4j
@RestController
public class ScopeController {  // 25 고정

    @Autowired private PerApp perApp;
    @Autowired private PerRequest perRequest;
    @Autowired private PerSession perSession;
    
    @RequestMapping("/scope/")
    public CommonResponse index(HttpServletRequest request) {
        var r = new CommonResponse();
        r.setPerApp(perApp.getNum());
        r.setPerRequest(perRequest.getNum());
        r.setPerSession(perSession.getNum());
        log.info("session  : {}", request.getSession().getId());
        log.info("response : {}", r);
        return r;
    }

/*
13:45:18.867 [http-nio-18888-exec-3] INFO  i.g.d.e.controller.ScopeController - session  : 5ED4F9A63DF466CFD1130D6656C29C8A
13:45:18.868 [http-nio-18888-exec-3] INFO  i.g.d.e.controller.ScopeController - response : CommonResponse(perApp=0, perRequest=0, perSession=0)
13:45:21.483 [http-nio-18888-exec-2] INFO  i.g.d.e.controller.ScopeController - session  : 5ED4F9A63DF466CFD1130D6656C29C8A
13:45:21.483 [http-nio-18888-exec-2] INFO  i.g.d.e.controller.ScopeController - response : CommonResponse(perApp=0, perRequest=1, perSession=0)
13:45:24.017 [http-nio-18888-exec-1] INFO  i.g.d.e.controller.ScopeController - session  : 4CAD55CEF2F70CEE5F84F97AD74052BC
13:45:24.017 [http-nio-18888-exec-1] INFO  i.g.d.e.controller.ScopeController - response : CommonResponse(perApp=0, perRequest=2, perSession=1)
13:45:24.550 [http-nio-18888-exec-5] INFO  i.g.d.e.controller.ScopeController - session  : 4CAD55CEF2F70CEE5F84F97AD74052BC
13:45:24.550 [http-nio-18888-exec-5] INFO  i.g.d.e.controller.ScopeController - response : CommonResponse(perApp=0, perRequest=3, perSession=1)
13:45:26.410 [http-nio-18888-exec-4] INFO  i.g.d.e.controller.ScopeController - session  : 5ED4F9A63DF466CFD1130D6656C29C8A
13:45:26.410 [http-nio-18888-exec-4] INFO  i.g.d.e.controller.ScopeController - response : CommonResponse(perApp=0, perRequest=4, perSession=0)
13:45:27.838 [http-nio-18888-exec-6] INFO  i.g.d.e.controller.ScopeController - session  : 4CAD55CEF2F70CEE5F84F97AD74052BC
13:45:27.838 [http-nio-18888-exec-6] INFO  i.g.d.e.controller.ScopeController - response : CommonResponse(perApp=0, perRequest=5, perSession=1)
*/
    
    @Autowired private PerMinute perMinute;
    
    @RequestMapping("/scope/minute")
    public String minute() {
        log.info("PerMinute : {}", perMinute.getNum());
        return String.valueOf(perMinute.getNum());
    }
/*
15:33:11.173 [http-nio-18888-exec-8] INFO  i.g.d.e.controller.ScopeController - PerMinute : 0
15:33:59.853 [http-nio-18888-exec-10] INFO  i.g.d.e.controller.ScopeController - PerMinute : 0
15:34:01.180 [http-nio-18888-exec-1] INFO  i.g.d.e.controller.ScopeController - PerMinute : 1
15:41:07.724 [http-nio-18888-exec-6] INFO  i.g.d.e.controller.ScopeController - PerMinute : 2
15:47:27.494 [http-nio-18888-exec-9] INFO  i.g.d.e.controller.ScopeController - PerMinute : 3
*/
}
