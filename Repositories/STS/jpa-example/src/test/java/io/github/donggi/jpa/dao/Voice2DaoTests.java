package io.github.donggi.jpa.dao;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.donggi.jpa.entity.Voice2;
import io.github.donggi.jpa.entity.VoiceWithActor2;
import io.github.donggi.jpa.entity.VoiceWithCue2;

@SpringBootTest
@TestMethodOrder(OrderAnnotation.class)
class Voice2DaoTests {

    @Autowired
    private Voice2Dao voice2Dao;
    @Autowired
    private VoiceWithCue2Dao voiceWithCue2Dao;
    @Autowired
    private VoiceWithActor2Dao voiceWithActor2Dao;
    

    @Test
    @Order(1000)
    void insert() {
        voice2Dao.deleteAll();
        var voiceId = 1L;
        voice2Dao.save(new Voice2(voiceId++, "Hello World 1"));
        voiceWithCue2Dao.save(VoiceWithCue2.builder().voiceId(voiceId++).message("Hello World 2").cueName("Voice001").build());
        voiceWithCue2Dao.save(VoiceWithCue2.builder().voiceId(voiceId++).message("Hello World 3").cueName("Voice002").build());
        voiceWithActor2Dao.save(VoiceWithActor2.builder().voiceId(voiceId++).message("Hello World 4").voiceActor("Actor001").build());
    }
    
    @Test
    @Order(1001)
    void select() {
        assertTrue(voice2Dao.count() == 4);
        assertTrue(voiceWithCue2Dao.count() == 2);
        assertTrue(voiceWithActor2Dao.count() == 1);
    }
    
    @Test
    @Order(1002)
    void oneMoreTime() {
        insert();
        select();
    }
}
