package test;

import java.sql.DriverManager;

import org.junit.jupiter.api.Test;

class JDBCTest {
    final String URL = "jdbc:mysql://localhost:3306/test?autoReconnect=true&useSSL=false";
    final String ID = "root";
    final String PW = "root";

    @Test
    void statementTest() throws Exception {
        Class.forName("com.mysql.jdbc.Driver");
        try (
                var conn = DriverManager.getConnection(URL, ID, PW);
                var stmt = conn.createStatement();)
        {
            var result = stmt.executeQuery("select * from t_user");
            while (result.next()) {
                // 다음 행 이동, 이전 행이 없으면 첫번째 행으로 이동. 이동한 위치에 데이터가 있으면 true
                System.out.printf(
                        "id : %s, pw : %s, email : %s, add_date : %s\n",
                        result.getString("user_id"),
                        result.getString("pw"),
                        result.getString("email"),
                        result.getDate("add_date"));
            }
        }
    }

    @Test
    void preparedStatementTest() throws Exception {
        Class.forName("com.mysql.jdbc.Driver");
        try (
                var conn = DriverManager.getConnection(URL, ID, PW);
                var stmt = conn.prepareStatement("select * from t_user where user_id = ?");)
        {
            stmt.setString(1, "1");
            var result = stmt.executeQuery();
            while (result.next()) {
                System.out.printf(
                        "id : %s, pw : %s, email : %s, add_date : %s\n",
                        result.getString("user_id"),
                        result.getString("pw"),
                        result.getString("email"),
                        result.getDate("add_date"));
            }
        }
    }

}
