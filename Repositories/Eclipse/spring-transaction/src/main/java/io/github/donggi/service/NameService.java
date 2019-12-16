package io.github.donggi.service;

import java.sql.SQLException;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.support.JdbcDaoSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NameService extends JdbcDaoSupport {

    @Autowired
    private DataSource dataSource;

    @PostConstruct
    private void initialize() {
        setDataSource(dataSource);
    }

    @Transactional
    public void success() throws SQLException {
        getJdbcTemplate().execute("insert into name (name) values ('valid name 1'), ('valid name 2')");
    }

    @Transactional
    public void fail() throws SQLException {
        getJdbcTemplate().execute("insert into name (name) values ('invalid name 1'), ('invalid name 2')");
        justThrow();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void justThrow() {
        ExceptionUtils.rethrow(new RuntimeException("그냥 던짐"));
    }

}
