package io.github.donggi.jpa.dao;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.transaction.Transactional;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.donggi.jpa.entity.GachaOdds;
import io.github.donggi.jpa.entity.GachaWithOdds;

@SpringBootTest
@TestMethodOrder(OrderAnnotation.class)
class GachaWithOddsDaoTests {

    private static Long gachaId;
    
    @Autowired
    private GachaWithOddsDao gachaWithOddsDao;
    @Autowired
    private GachaOddsDao gachaOddsDao;


    @Test
    @Order(1000)
    void insert() {
        var gacha = new GachaWithOdds();
        gacha.setGachaId(System.nanoTime());
        gacha.setGachaName("Gacha " + gacha.getGachaId());
        gacha = gachaWithOddsDao.save(gacha);
        gachaId = gacha.getGachaId();
        
        gachaOddsDao.save(new GachaOdds(new GachaOdds.ID(gachaId, 3), "memo3", 1L, 3));
        gachaOddsDao.save(new GachaOdds(new GachaOdds.ID(gachaId, 1), "memo1", 2L, 1));
        gachaOddsDao.save(new GachaOdds(new GachaOdds.ID(gachaId, 4), "memo4", 1L, 4));
        gachaOddsDao.save(new GachaOdds(new GachaOdds.ID(gachaId, 2), "memo2", 2L, 2));
    }

    @Test
    @Order(1001)
    @Transactional
    void select() {
        var gacha = gachaWithOddsDao.findById(gachaId).get();
        assertTrue(gacha.getObjectIds().size() == 4);
        assertTrue(gacha.getObjectIdList().size() == 4);
        assertTrue(gacha.getObjectIdSet().size() == 2);
        assertTrue(gacha.getObjectIdMap().size() == 4);
        assertTrue(gacha.getMinimalOddList().size() == 4);
        
        var minimalOdds = gachaOddsDao.findMinimalOdds(gachaId); 
        assertTrue(gacha.getMinimalOddList().stream().allMatch(x -> minimalOdds.contains(x)));
    }

}
