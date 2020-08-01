package io.github.donggi.jpa.dao;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import io.github.donggi.jpa.entity.ApiLog;
import io.github.donggi.jpa.enums.ApiResultCode;

@SpringBootTest
@TestMethodOrder(OrderAnnotation.class)
class ApiLogDaoTests {

    @Autowired
    private ApiLogDao apiLogDao;

    @Test
    @Order(1000)
    void insert() {
        var log = new ApiLog();
        log.setUserId(1000L);
        log.setRequestApi("/test");
        log.setApiResult(ApiResultCode.BAD_REQUEST);
        apiLogDao.save(log);
    }
    
    @Test
    @Order(1001)
    void select() {
        var logs = apiLogDao.findAll();
        assertTrue(logs.size() > 0);
        logs.forEach(System.out::println);
        // ApiLog(logNo=1, userId=1000, requestApi=/test, requestParam=null, apiResult=BAD_REQUEST, addDate=2020-08-01 16:50:00.479)
    }
}
