package io.github.donggi.jpa.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.UserWithCard2;

@Repository
public interface UserWithCard2Dao extends JpaRepository<UserWithCard2, Long> {}
