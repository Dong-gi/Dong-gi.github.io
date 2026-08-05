package io.github.donggi.jpa.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.UserCard4;

@Repository
public interface UserCard4Dao extends JpaRepository<UserCard4, Long> {}
