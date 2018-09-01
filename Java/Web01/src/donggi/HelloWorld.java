package donggi;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class HelloWorld extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html; charset=utf-8");
        var w = resp.getWriter();
        w.println("<html lnag=\"kor\">");
        w.println("<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/></head>");
        w.println("<body>");
        w.println("<p>Hello World!</p>");
        w.println("</body>");
        w.println("</html>");
    }
}
