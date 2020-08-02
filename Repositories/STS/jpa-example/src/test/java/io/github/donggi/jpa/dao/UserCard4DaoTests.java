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

import io.github.donggi.jpa.entity.UserCard4;
import io.github.donggi.jpa.entity.UserWithCard2;

@SpringBootTest
@TestMethodOrder(OrderAnnotation.class)
class UserCard4DaoTests {

    private static Long userId1;
    private static Long userId2;
    private static Long userCardId;

    @Autowired
    private UserCard4Dao userCard4Dao;
    @Autowired
    private UserWithCard2Dao userWithCard2Dao;
    

    @Test
    @Order(1000)
    void manyToOneTest2_insert() {
        var user = new UserWithCard2();
        user.setNickname("ManyToOne2_insert");
        user = userWithCard2Dao.save(user);
        userId1 = user.getUserId();
        
        var userCard = new UserCard4();
        userCard.setOwner(user);
        userCard = userCard4Dao.save(userCard);
        userCardId = userCard.getUserCardId();
    }

    @Test
    @Order(1001)
    @Transactional
    @Rollback(false)
    void manyToOneTest2_update() {
        var user = new UserWithCard2();
        user.setNickname("ManyToOne2_update");
        user = userWithCard2Dao.save(user);
        userId2 = user.getUserId();

        var userCard = userCard4Dao.findById(userCardId).get();
        assertTrue(userCard.getOwner().getNickname().equals("ManyToOne2_insert"));
        userCard.setOwner(user);
        
        userCard = new UserCard4();
        userCard.setOwner(user);
        userCard4Dao.save(userCard);
    }

    @Test
    @Order(1002)
    @Transactional
    void manyToOneTest2_select() {
        var userCard = userCard4Dao.findById(userCardId).get();
        assertTrue(userCard.getOwner().getNickname().equals("ManyToOne2_update"));
        
        var beforeUser = userWithCard2Dao.findById(userId1).get();
        assertTrue(beforeUser.getCards().size() == 0);
        
        var afterUser = userWithCard2Dao.findById(userId2).get();
        assertTrue(afterUser.getCards().size() == 2);
        assertTrue(afterUser.getCards().stream().anyMatch(x -> x.equals(userCard)));
    }

}
