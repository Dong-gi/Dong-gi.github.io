package io.github.donggi.jpa.dao;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.donggi.jpa.entity.Voice1;
import io.github.donggi.jpa.entity.VoiceWithActor1;
import io.github.donggi.jpa.entity.VoiceWithCue1;

@SpringBootTest
@TestMethodOrder(OrderAnnotation.class)
class Voice1DaoTests {

    @Autowired
    private Voice1Dao voice1Dao;
    @Autowired
    private VoiceWithCue1Dao voiceWithCue1Dao;
    @Autowired
    private VoiceWithActor1Dao voiceWithActor1Dao;
    

    @Test
    @Order(1000)
    void insert() {
        voice1Dao.deleteAll();
        var voiceId = 1L;
        voice1Dao.save(new Voice1(voiceId++, "Hello World 1"));
        voiceWithCue1Dao.save(VoiceWithCue1.builder().voiceId(voiceId++).message("Hello World 2").cueName("Voice001").build());
        voiceWithCue1Dao.save(VoiceWithCue1.builder().voiceId(voiceId++).message("Hello World 3").cueName("Voice002").build());
        voiceWithActor1Dao.save(VoiceWithActor1.builder().voiceId(voiceId++).message("Hello World 4").voiceActor("Actor001").build());
    }
    
    @Test
    @Order(1001)
    void select() {
        assertTrue(voice1Dao.count() == 4);
        assertTrue(voiceWithCue1Dao.count() == 2);
        assertTrue(voiceWithActor1Dao.count() == 1);
    }
    
    @Test
    @Order(1002)
    void oneMoreTime() {
        insert();
        select();
    }
}
