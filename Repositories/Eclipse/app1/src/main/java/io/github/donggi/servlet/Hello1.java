package io.github.donggi.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Hello1 extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html; charset=utf-8");
        var w = resp.getWriter();
        w.println("<html lnag=\"ko\">");
        w.println("<head>");
        w.println("    <meta charset=\"utf-8\">");
        w.println("</head>");
        w.println("<body>");
        w.println("    <p>안녕하세요!</p>");
        w.println("</body>");
        w.println("</html>");
    }

}
