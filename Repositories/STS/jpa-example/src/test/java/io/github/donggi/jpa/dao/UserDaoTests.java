package io.github.donggi.jpa.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.Rollback;

import io.github.donggi.jpa.entity.User1;
import io.github.donggi.jpa.entity.User2;

@SpringBootTest
class UserDaoTests {

    @Autowired
    private EntityManager manager;
    @Autowired
    private User1Dao user1Dao;
    @Autowired
    private User2Dao user2Dao;


    @Test
    void managerExists() {
        assertTrue(manager != null);
        assertFalse(manager.isJoinedToTransaction());
    }

    @Test
    void createUser1() {
        var user = new User1();
        assertTrue(user.getUserId() == null);
        assertTrue(user.getAddDate() == null);
        
        var nickname = "At" + System.nanoTime();
        user.setNickname(nickname);
        user = user1Dao.save(user);
        assertTrue(user.getUserId() != null);   // ← 설정됨
        assertTrue(user.getAddDate() == null);  // ← 설정되지 않음
        assertTrue(user1Dao.findByNickname(nickname).size() > 0);
    }

    @Test
    void createUser2() {
        var user = new User2();
        assertTrue(user.getUserId() == null);
        assertTrue(user.getAddDate() == null);
        
        var nickname = "At" + System.nanoTime();
        user.setNickname(nickname);
        user = user2Dao.save(user);
        assertTrue(user.getUserId() != null);   // ← 설정됨
        assertTrue(user.getAddDate() != null);  // ← 설정됨
        assertTrue(user2Dao.findByNickname(nickname).size() > 0);
    }

    @Test
    @Transactional
    @Rollback(false) // 테스트 자동 롤백하지 않도록 설정
    void updateUser() {
        assertTrue(manager.isJoinedToTransaction());
        var user = user2Dao.findAll(PageRequest.of(0, 1)).getContent().get(0);
        user.setNickname("New nickname");
        System.out.println(user);
        /*
         User2(userId=36, nickname=New nickname, addDate=2020-02-19 22:33:32.376711, updDate=2020-07-29 10:42:22.541)
         Hibernate: update jpa_user2 set add_date=?, nickname=?, upd_date=? where user_id=?
         */
    }
    
}
