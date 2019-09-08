package servlet;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/callOther/*")
public class CallOther extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html; charset=utf-8");
        var w = resp.getWriter();
        switch (req.getPathInfo()) {
        case "/refreshHeader":
            resp.addHeader("Refresh", "3;url=../hello1");
            w.println("Refresh : 3초 뒤 /hello1으로 이동합니다.");
            return;
        case "/refreshMeta":
            w.println("<meta http-equiv=\"Refresh\" content=\"3; url=../hello1\">");
            w.println("Refresh : 3초 뒤 /hello1으로 이동합니다.");
            return;
        case "/redirect":
            resp.sendRedirect("bye?name=" + URLEncoder.encode("한글 이름", StandardCharsets.UTF_8));
            break;
        case "/forward":
            req.getRequestDispatcher("bye?name=포워드").forward(req, resp);
            break;
        case "/include":
            req.getRequestDispatcher("../hello1").include(req, resp);
            w.println("<br>더 일할 수 있다.");
            break;
        case "/bye":
            req.setCharacterEncoding("utf-8");
            w.println("<html lnag=\"ko\">");
            w.println("<head><meta charset=utf-8\"/></head>");
            w.println("Bye...! " + Optional.ofNullable(req.getParameter("name")).orElse("익명"));
        }
    }
}