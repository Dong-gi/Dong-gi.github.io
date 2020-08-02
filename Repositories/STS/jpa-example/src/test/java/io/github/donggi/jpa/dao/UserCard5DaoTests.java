package io.github.donggi.jpa.dao;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.transaction.Transactional;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import io.github.donggi.jpa.entity.UserCard5;
import io.github.donggi.jpa.entity.UserWithCard3;

@SpringBootTest
@TestMethodOrder(OrderAnnotation.class)
class UserCard5DaoTests {

    private static Long userId;

    @Autowired
    private UserWithCard3Dao userWithCard3Dao;
    @Autowired
    private UserCard5Dao userCard5Dao;


    @Test
    @Order(1000)
    void oneToManyTest_insert() {
        var user = new UserWithCard3();
        user.setNickname("OneToMany");
        user = userWithCard3Dao.save(user);
        userId = user.getUserId();
        
        var userCard = new UserCard5();
        userCard.setUserId(userId);
        userCard = userCard5Dao.save(userCard);

        userCard = new UserCard5();
        userCard.setUserId(userId);
        userCard = userCard5Dao.save(userCard);
    }

    @Test
    @Order(1002)
    @Transactional
    void oneToManyTest_select() {
        var user = userWithCard3Dao.findById(userId).get();
        var cards = user.getCards();
        assertTrue(cards.size() == 2);
        assertTrue(cards.stream().allMatch(x -> x.getUserId().equals(userId)));
    }

}
