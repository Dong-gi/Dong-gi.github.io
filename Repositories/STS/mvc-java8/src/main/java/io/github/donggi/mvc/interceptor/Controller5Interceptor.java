package io.github.donggi.mvc.interceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.HandlerInterceptor;
import io.github.donggi.mvc.controller.Controller5;

public class Controller5Interceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        request.setAttribute("request", new Controller5.C5Request(123L, "value"));
        return true;
    }
}