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

import io.github.donggi.jpa.entity.UserCard2;
import io.github.donggi.jpa.entity.UserWithCard1;

@SpringBootTest
@TestMethodOrder(OrderAnnotation.class)
class UserCard2DaoTests {

    private static Long userId1;
    private static Long userId2;
    private static Long userCardId;

    @Autowired
    private UserCard2Dao userCard2Dao;
    @Autowired
    private UserWithCard1Dao userWithCard1Dao;
    

    @Test
    @Order(1000)
    void oneToOneTest2_insert() {
        var user = new UserWithCard1();
        user.setNickname("OneToOne2_insert");
        user = userWithCard1Dao.save(user);
        userId1 = user.getUserId();
        
        var userCard = new UserCard2();
        userCard.setOwner(user);
        userCard = userCard2Dao.save(userCard);
        userCardId = userCard.getUserCardId();
    }

    @Test
    @Order(1001)
    @Transactional
    @Rollback(false)
    void oneToOneTest2_update() {
        var user = new UserWithCard1();
        user.setNickname("OneToOne2_update");
        user = userWithCard1Dao.save(user);
        userId2 = user.getUserId();

        var userCard = userCard2Dao.findById(userCardId).get();
        assertTrue(userCard.getOwner().getNickname().equals("OneToOne2_insert"));
        userCard.setOwner(user);
    }

    @Test
    @Order(1002)
    void oneToOneTest2_select() {
        var userCard = userCard2Dao.findById(userCardId).get();
        assertTrue(userCard.getOwner().getNickname().equals("OneToOne2_update"));
        
        var beforeUser = userWithCard1Dao.findById(userId1).get();
        assertTrue(beforeUser.getCard() == null);
        
        var afterUser = userWithCard1Dao.findById(userId2).get();
        assertTrue(afterUser.getCard().equals(userCard));
    }

}
