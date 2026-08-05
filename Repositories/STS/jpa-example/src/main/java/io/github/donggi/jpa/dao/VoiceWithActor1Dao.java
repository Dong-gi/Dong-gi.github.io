package io.github.donggi.jpa.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.VoiceWithActor1;

@Repository
public interface VoiceWithActor1Dao extends JpaRepository<VoiceWithActor1, Long> {}
