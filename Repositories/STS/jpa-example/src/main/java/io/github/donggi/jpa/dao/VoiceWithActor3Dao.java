package io.github.donggi.jpa.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.VoiceWithActor3;

@Repository
public interface VoiceWithActor3Dao extends JpaRepository<VoiceWithActor3, Long> {}
