package io.github.donggi.jpa.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.VoiceWithCue1;

@Repository
public interface VoiceWithCue1Dao extends JpaRepository<VoiceWithCue1, Long> {}
