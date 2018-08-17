package donggi;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Optional;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/hellobye/*")
public class HelloBye extends HttpServlet {

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.setContentType("text/html; charset=utf-8");
		var w = resp.getWriter();
		switch(req.getPathInfo()) {
		case "/hello1":
			resp.addHeader("Refresh", "3;url=bye");
			w.println("Hello...!");
			break;
		case "/hello2":
			w.println("<meta http-equiv=\"Refresh\" content=\"3; url=bye\">");
			w.println("Hello...!");
			break;
		case "/hello3":
			resp.sendRedirect("bye?name=" + URLEncoder.encode("한글 이름", "utf-8"));
			break;
		case "/hello4":
			req.getRequestDispatcher("bye?name=forward").forward(req, resp);
			break;
		case "/hello5":
			req.getRequestDispatcher("../hello").include(req, resp);
			w.println("더 일할 수 있다.");
			break;
		case "/bye":
			req.setCharacterEncoding("utf-8");
			w.println("<html lnag=\"kor\">");
	        w.println("<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/></head>");
	        w.println("Bye...! " + Optional.ofNullable(req.getParameter("name")).orElse(""));
		}
	}
	
}
