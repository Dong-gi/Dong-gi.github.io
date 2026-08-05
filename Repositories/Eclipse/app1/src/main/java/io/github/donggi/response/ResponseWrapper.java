package io.github.donggi.response;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

public class ResponseWrapper extends HttpServletResponseWrapper {
    private HttpServletResponse response;

    public ResponseWrapper(HttpServletResponse response) {
        super(response);
        this.response = response;
    }

    /* 비즈니스 로직 */
}