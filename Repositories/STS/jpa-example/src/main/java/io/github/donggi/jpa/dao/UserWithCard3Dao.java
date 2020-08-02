package io.github.donggi.jpa.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.UserWithCard3;

@Repository
public interface UserWithCard3Dao extends JpaRepository<UserWithCard3, Long> {}
