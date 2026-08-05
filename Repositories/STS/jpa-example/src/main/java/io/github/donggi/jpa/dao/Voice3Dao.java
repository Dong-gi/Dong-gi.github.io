package io.github.donggi.jpa.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.Voice3;

@Repository
public interface Voice3Dao extends JpaRepository<Voice3, Long> {}
