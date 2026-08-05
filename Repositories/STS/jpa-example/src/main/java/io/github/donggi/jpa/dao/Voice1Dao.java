package io.github.donggi.jpa.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.Voice1;

@Repository
public interface Voice1Dao extends JpaRepository<Voice1, Long> {}
