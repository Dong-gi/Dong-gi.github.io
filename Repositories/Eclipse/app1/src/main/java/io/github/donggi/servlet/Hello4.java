package io.github.donggi.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.commons.dbcp2.BasicDataSourceFactory;

@WebServlet("/hello4")
public class Hello4 extends HttpServlet {

    private static DataSource testDataSource;

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            testDataSource = BasicDataSourceFactory.createDataSource(new Properties() {{
                setProperty("driverClassName", "com.mysql.cj.jdbc.Driver");
                setProperty("url", "jdbc:mysql://127.0.0.1:3306/test?characterEncoding=UTF-8&serverTimezone=UTC&autoReconnect=true");
                setProperty("username","root");
                setProperty("password","root");
            }});
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html; charset=utf-8");
        try(
                var conn = testDataSource.getConnection();
                var stmt = conn.createStatement();
                var result = stmt.executeQuery("select distinct name from name");)
        {
            while(result.next())
                resp.getWriter().append("안녕하세요! ").append(result.getString(1)).append("<br>");
        } catch (SQLException e) {
            e.printStackTrace(resp.getWriter());
        }
    }
}
