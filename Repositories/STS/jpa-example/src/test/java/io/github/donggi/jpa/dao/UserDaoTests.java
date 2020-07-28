package io.github.donggi.jpa.dao;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
    }
    
	@Test
	void createUser1() {
	    var user = new User1();
	    var nickname = "At" + System.nanoTime();
	    user.setNickname(nickname);
	    user1Dao.save(user);
	    assertTrue(user1Dao.findByNickname(nickname).size() > 0);
	}

	@Test
    void createUser2() {
        var user = new User2();
        var nickname = "At" + System.nanoTime();
        user.setNickname(nickname);
        user2Dao.save(user);
        assertTrue(user2Dao.findByNickname(nickname).size() > 0);
    }

}
