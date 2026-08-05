package io.github.donggi.request;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

public class RequestWrapper extends HttpServletRequestWrapper {
    private HttpServletRequest request;

    public RequestWrapper(HttpServletRequest request) {
        super(request);
        this.request = request;
    }

    /* 비즈니스 로직 */
}