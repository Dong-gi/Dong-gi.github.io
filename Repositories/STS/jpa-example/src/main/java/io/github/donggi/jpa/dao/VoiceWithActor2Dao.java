package io.github.donggi.jpa.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.VoiceWithActor2;

@Repository
public interface VoiceWithActor2Dao extends JpaRepository<VoiceWithActor2, Long> {}
