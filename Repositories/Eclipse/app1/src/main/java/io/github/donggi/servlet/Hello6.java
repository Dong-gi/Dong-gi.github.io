package io.github.donggi.servlet;

import java.io.IOException;
import java.sql.SQLException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

@WebServlet("/hello6")
public class Hello6 extends HttpServlet {
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html; charset=utf-8");
        try {
            Context context = new InitialContext();
            DataSource dataSource = (DataSource) context.lookup("java:comp/env/jdbc/MySQL/localhost/test");
            try(
                    var conn = dataSource.getConnection();
                    var stmt = conn.createStatement();
                    var result = stmt.executeQuery("select distinct name from name");)
            {
                while(result.next())
                    resp.getWriter().append("안녕하세요! ").append(result.getString(1)).append("<br>");
            }
        } catch (SQLException | NamingException e) {
            e.printStackTrace(resp.getWriter());
        }
    }
} 
