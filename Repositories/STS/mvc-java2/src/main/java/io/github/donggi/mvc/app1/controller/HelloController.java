package io.github.donggi.mvc.app1.controller;

import java.io.IOException;
import java.util.Date;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class HelloController extends HttpServlet {
    private static final long serialVersionUID = 1L;
    
    @Resource(name = "rootMsg")
    private String rootMsg;
    @Resource(name = "app1Msg")
    private String app1Msg;

    @RequestMapping("/hello")
    public void hello(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html; charset=utf-8");
        var w = resp.getWriter();
        w.println("<html lnag=\"ko\">");
        w.println("<head><meta charset=\"utf-8\"></head>");
        w.println(String.format("<body>%s,%s@%s</body>", rootMsg, app1Msg, new Date()));
        w.println("</html>");
    }
}
