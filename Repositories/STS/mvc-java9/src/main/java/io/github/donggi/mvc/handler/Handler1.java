package io.github.donggi.mvc.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class Handler1 {
    public ServerResponse get(ServerRequest request) {
        log.debug("get : " + request.servletRequest().getRequestURI());
        return ServerResponse.ok().build();
    }
    public ServerResponse post(ServerRequest request) {
        log.debug("post : " + request.servletRequest().getRequestURI());
        return ServerResponse.ok().build();
    }
}
