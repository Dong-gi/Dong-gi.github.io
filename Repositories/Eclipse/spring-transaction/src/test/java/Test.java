import static org.junit.Assert.assertTrue;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import io.github.donggi.service.NameService;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:applicationContext.xml")
public class Test {

    @Autowired
    private DataSource dataSource;
    @Autowired
    private NameService nameService;

    @org.junit.Test
    public void connectionTest() throws SQLException {
        try(var conn = dataSource.getConnection()) {
            assertTrue(conn.isValid(100));
        }
    }

    @org.junit.Test
    public void successTest() throws SQLException {
        nameService.success();
    }

    @org.junit.Test
    public void failTest() throws SQLException {
        nameService.fail();
    }

/*
53  valid name 1
54  valid name 2
57  valid name 1
58  valid name 2
61  valid name 1
62  valid name 2
65  valid name 1
66  valid name 2
69  valid name 1
70  valid name 2
 */

}
