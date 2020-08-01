package io.github.donggi.jpa.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.User3;

@Repository
public interface User3Dao extends JpaRepository<User3, Long> {}
