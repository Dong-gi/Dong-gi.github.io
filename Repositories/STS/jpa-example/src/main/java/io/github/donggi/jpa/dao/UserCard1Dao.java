package io.github.donggi.jpa.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.UserCard1;

@Repository
public interface UserCard1Dao extends JpaRepository<UserCard1, Long> {}
