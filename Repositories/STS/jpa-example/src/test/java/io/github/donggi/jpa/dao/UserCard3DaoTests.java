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
import io.github.donggi.jpa.entity.UserCard3;

@SpringBootTest
@TestMethodOrder(OrderAnnotation.class)
class UserCard3DaoTests {

    private static Long userCardId;

    @Autowired
    private User3Dao user3Dao;
    @Autowired
    private UserCard3Dao userCard3Dao;


    @Test
    @Order(1000)
    void manyToOneTest1_insert() {
        var user = new User3();
        user.setNickname("ManyToOne1_insert");
        user = user3Dao.save(user);

        var userCard = new UserCard3();
        userCard.setOwner(user);
        userCard = userCard3Dao.save(userCard);

        userCardId = userCard.getUserCardId();
    }

    @Test
    @Order(1001)
    @Transactional
    @Rollback(false)
    void manyToOneTest1_update() {
        var user = new User3();
        user.setNickname("ManyToOne1_update");
        user = user3Dao.save(user);

        var userCard = userCard3Dao.findById(userCardId).get();
        assertTrue(userCard.getOwner().getNickname().equals("ManyToOne1_insert"));
        userCard.setOwner(user);
    }

    @Test
    @Order(1002)
    void manyToOneTest1_select() {
        var userCard = userCard3Dao.findById(userCardId).get();
        assertTrue(userCard.getOwner().getNickname().equals("ManyToOne1_update"));
    }

}
