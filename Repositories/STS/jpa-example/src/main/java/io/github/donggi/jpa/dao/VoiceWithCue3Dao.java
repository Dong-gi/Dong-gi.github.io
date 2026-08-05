package io.github.donggi.jpa.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.VoiceWithCue3;

@Repository
public interface VoiceWithCue3Dao extends JpaRepository<VoiceWithCue3, Long> {}
