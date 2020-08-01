package io.github.donggi.jpa.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.UserCard2;

@Repository
public interface UserCard2Dao extends JpaRepository<UserCard2, Long> {}
