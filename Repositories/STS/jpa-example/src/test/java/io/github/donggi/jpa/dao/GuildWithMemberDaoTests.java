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

import io.github.donggi.jpa.entity.GachaOdds;
import io.github.donggi.jpa.entity.GachaWithOdds;
import io.github.donggi.jpa.entity.GuildWithMember;
import io.github.donggi.jpa.entity.User2;

@SpringBootTest
@TestMethodOrder(OrderAnnotation.class)
class GuildWithMemberDaoTests {

    private static Long guildId;
    
    @Autowired
    private GuildWithMemberDao guildWithMemberDao;
    @Autowired
    private User2Dao user2Dao;


    @Test
    @Order(1000)
    @Transactional
    @Rollback(false)
    void insert() {
        var user = new User2();
        user.setNickname("Guild Member");
        user = user2Dao.save(user);
        
        var guild = new GuildWithMember();
        guild.setGuildName("Guild Name");
        guild = guildWithMemberDao.save(guild);
        guild.getMembers().add(user);
        guildId = guild.getGuildId();
    }

    @Test
    @Order(1001)
    @Transactional
    void select() {
        assertTrue(guildWithMemberDao.findById(guildId).get().getMembers().size() > 0);
    }

}
