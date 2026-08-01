package io.github.donggi.app1.controller;

import java.io.IOException;
import java.util.Date;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class HelloController extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html; charset=utf-8");
        var w = resp.getWriter();
        w.println("<html lnag=\"ko\">");
        w.println("<head><meta charset=\"utf-8\"></head>");
        w.println(String.format("<body>안녕 세상@%s</body>", new Date()));
        w.println("</html>");
    }
}
