package io.github.donggi.jpa.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.UserWithCard1;

@Repository
public interface UserWithCard1Dao extends JpaRepository<UserWithCard1, Long> {}
