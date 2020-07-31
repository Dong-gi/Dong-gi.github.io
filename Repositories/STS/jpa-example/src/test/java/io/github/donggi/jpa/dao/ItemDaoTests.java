package io.github.donggi.jpa.dao;

import javax.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;

import io.github.donggi.jpa.entity.Item1;
import io.github.donggi.jpa.entity.Item2;
import io.github.donggi.jpa.entity.Item3;
import io.github.donggi.jpa.entity.Item4;
import io.github.donggi.jpa.entity.Item5;
import io.github.donggi.jpa.entity.ItemInfo;
import io.github.donggi.jpa.entity.ItemInfo3;
import io.github.donggi.jpa.entity.ItemInfo3Key;
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
    @Autowired
    private Item4Dao item4Dao;
    @Autowired
    private Item5Dao item5Dao;
    @Autowired
    private ItemInfo3Dao itemInfo3Dao;


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

    @Test
    void secondaryTableTest() {
        var item4 = new Item4();
        item4.setItemInfo(new ItemInfo("Some Info1"));
        item4Dao.save(item4);
        
        var item5 = new Item5();
        item5.setItemInfo(new ItemInfo("Some Info2"));
        item5Dao.save(item5);
    }

    @Test
    void compositeKeyTest() {
        var itemInfo3 = new ItemInfo3();
        itemInfo3.setId(new ItemInfo3Key(System.nanoTime(), 100));
        itemInfo3.setInfo1("Some Info3");
        itemInfo3Dao.save(itemInfo3);
    }
}
