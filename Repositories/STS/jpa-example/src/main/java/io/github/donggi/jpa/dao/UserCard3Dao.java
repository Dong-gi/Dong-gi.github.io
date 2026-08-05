package io.github.donggi.jpa.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.UserCard3;

@Repository
public interface UserCard3Dao extends JpaRepository<UserCard3, Long> {}
