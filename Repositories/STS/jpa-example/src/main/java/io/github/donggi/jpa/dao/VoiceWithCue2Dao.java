package io.github.donggi.jpa.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.VoiceWithCue2;

@Repository
public interface VoiceWithCue2Dao extends JpaRepository<VoiceWithCue2, Long> {}
