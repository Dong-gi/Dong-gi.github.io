package io.github.donggi.servlet;

import java.util.logging.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;

@WebServlet(urlPatterns = { "/no_need_to_call_this" }, loadOnStartup = 10)
public class Hello5 extends HttpServlet {

    private static final Logger logger = Logger.getLogger(Hello5.class.getCanonicalName());

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        logger.info("클라이언트 호출 없이도 초기화 진행!");
/*
9월 12, 2019 8:32:14 오후 servlet.Hello5 init
INFO: 클라이언트 호출 없이도 초기화 진행!
 */
    }
}
