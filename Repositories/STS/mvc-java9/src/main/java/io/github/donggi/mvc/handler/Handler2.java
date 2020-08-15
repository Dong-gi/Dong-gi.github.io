package io.github.donggi.mvc.handler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class Handler2 {
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class Request {
        private Long userId;
        private String msg;
    }
    public ServerResponse get(ServerRequest request) {
        log.debug("Params : " + request.params());
        var r = new ArrayList<Request>();
        r.add(new Request(123L, "안녕 세상1"));
        r.add(new Request(456L, "안녕 세상2"));
        return ServerResponse.ok().body(r, new ParameterizedTypeReference<List<Request>>() {});
    }
    public ServerResponse post(ServerRequest request) throws ServletException, IOException {
        log.debug("body(List) : " + request.body(new ParameterizedTypeReference<List<Request>>() {}));
        return ServerResponse.ok().build();
    }
/*
20:25:27.738 [http-nio-8080-exec-3] DEBUG o.s.web.servlet.DispatcherServlet - GET "/mvc-java9/app/2/get?k1=v1&k1=v2&k3=v3", parameters={masked}
20:25:27.771 [http-nio-8080-exec-3] DEBUG o.s.w.s.f.s.RouterFunctionMapping - Mapped to io.github.donggi.mvc.config.AppConfig$$Lambda$239/0x0000000800dec040@1afa048a
20:25:27.774 [http-nio-8080-exec-3] DEBUG i.github.donggi.mvc.handler.Handler2 - Params : {k3=[v3], k1=[v1, v2]}
    Client Get : [{"userId":123,"msg":"안녕 세상1"},{"userId":456,"msg":"안녕 세상2"}]

20:22:36.809 [http-nio-8080-exec-6] DEBUG o.s.web.servlet.DispatcherServlet - POST "/mvc-java9/app/2/post", parameters={}
    Clinet Post : [{"userId":123,"msg":"안녕 세상1"},{"userId":456,"msg":"안녕 세상2"}]
20:22:36.811 [http-nio-8080-exec-6] DEBUG o.s.w.s.f.s.RouterFunctionMapping - Mapped to io.github.donggi.mvc.config.AppConfig$$Lambda$385/0x0000000800ff0440@5768c43f
20:22:36.904 [http-nio-8080-exec-6] DEBUG i.github.donggi.mvc.handler.Handler2 - body(List) : [Handler2.Request(userId=123, msg=안녕 세상1), Handler2.Request(userId=456, msg=안녕 세상2)]
 */
}
