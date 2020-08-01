package io.github.donggi.jpa.dao;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.transaction.Transactional;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;

import io.github.donggi.jpa.entity.User3;
import io.github.donggi.jpa.entity.UserCard1;

@SpringBootTest
@TestMethodOrder(OrderAnnotation.class)
class UserCard1DaoTests {

    private static Long userCardId;

    @Autowired
    private User3Dao user3Dao;
    @Autowired
    private UserCard1Dao userCard1Dao;


    @Test
    @Order(1000)
    void oneToOneTest1_insert() {
        var user = new User3();
        user.setNickname("OneToOne1_insert");
        user = user3Dao.save(user);

        var userCard1 = new UserCard1();
        userCard1.setOwner(user);
        userCard1 = userCard1Dao.save(userCard1);

        userCardId = userCard1.getUserCardId();
    }

    @Test
    @Order(1001)
    @Transactional
    @Rollback(false)
    void oneToOneTest1_update() {
        var user = new User3();
        user.setNickname("OneToOne1_update");
        user = user3Dao.save(user);

        var userCard1 = userCard1Dao.findById(userCardId).get();
        assertTrue(userCard1.getOwner().getNickname().equals("OneToOne1_insert"));
        userCard1.setOwner(user);
    }

    @Test
    @Order(1002)
    void oneToOneTest1_select() {
        var userCard1 = userCard1Dao.findById(userCardId).get();
        assertTrue(userCard1.getOwner().getNickname().equals("OneToOne1_update"));
    }

}
