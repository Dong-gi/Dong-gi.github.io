package io.github.donggi.jpa.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.Voice2;

@Repository
public interface Voice2Dao extends JpaRepository<Voice2, Long> {}
