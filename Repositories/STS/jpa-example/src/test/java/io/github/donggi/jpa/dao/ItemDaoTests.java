package io.github.donggi.jpa.dao;

import javax.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;

import io.github.donggi.jpa.entity.Item1;
import io.github.donggi.jpa.entity.Item2;
import io.github.donggi.jpa.entity.Item3;
import io.github.donggi.jpa.entity.Price;
import io.github.donggi.jpa.entity.PricePair;

@SpringBootTest
class ItemDaoTests {

    @Autowired
    private Item1Dao item1Dao;
    @Autowired
    private Item2Dao item2Dao;
    @Autowired
    private Item3Dao item3Dao;


    @Test
    @Transactional
    @Rollback(false)
    void createItem() {
        var item1 = new Item1();
        var item2 = new Item2();
        item1.setPrice(new Price(100, 50));
        item2.setKoPrice(item1.getPrice());
        item1 = item1Dao.save(item1);
        
        item1.setPrice(new Price(200, 100));
        item2.setJpPrice(item1.getPrice());
        item2 = item2Dao.save(item2);
        
        var item3 = new Item3();
        item3.setPrice(new PricePair(item2.getKoPrice(), item2.getJpPrice()));
        item3Dao.save(item3);
    }

}
