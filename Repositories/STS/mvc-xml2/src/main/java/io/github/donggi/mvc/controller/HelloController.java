package io.github.donggi.mvc.controller;

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

    @Resource(name = "defaultMsg")
    private String defaultMsg;

    @RequestMapping("/hello")
    public void hello(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html; charset=utf-8");
        var w = resp.getWriter();
        w.println("<html lnag=\"ko\">");
        w.println("<head><meta charset=\"utf-8\"></head>");
        w.println(String.format("<body>%s@%s</body>", defaultMsg, new Date()));
        w.println("</html>");
    }
}
