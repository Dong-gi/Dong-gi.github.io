package io.github.donggi.jpa.dao;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.donggi.jpa.entity.Voice3;
import io.github.donggi.jpa.entity.VoiceWithActor3;
import io.github.donggi.jpa.entity.VoiceWithCue3;

@SpringBootTest
@TestMethodOrder(OrderAnnotation.class)
class Voice3DaoTests {

    @Autowired
    private Voice3Dao voice3Dao;
    @Autowired
    private VoiceWithCue3Dao voiceWithCue3Dao;
    @Autowired
    private VoiceWithActor3Dao voiceWithActor3Dao;
    

    @Test
    @Order(1000)
    void insert() {
        voice3Dao.deleteAll();
        var voiceId = 1L;
        voice3Dao.save(new Voice3(voiceId++, "Hello World 1"));
        voiceWithCue3Dao.save(VoiceWithCue3.builder().voiceId(voiceId++).message("Hello World 2").cueName("Voice001").build());
        voiceWithCue3Dao.save(VoiceWithCue3.builder().voiceId(voiceId++).message("Hello World 3").cueName("Voice002").build());
        voiceWithActor3Dao.save(VoiceWithActor3.builder().voiceId(voiceId++).message("Hello World 4").voiceActor("Actor001").build());
    }
    
    @Test
    @Order(1001)
    void select() {
        assertTrue(voice3Dao.count() == 4);
        assertTrue(voiceWithCue3Dao.count() == 2);
        assertTrue(voiceWithActor3Dao.count() == 1);
    }
    
    @Test
    @Order(1002)
    void oneMoreTime() {
        insert();
        select();
    }
}
