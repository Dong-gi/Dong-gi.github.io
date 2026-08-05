package io.github.donggi.jpa.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.User1;

@Repository
public interface User1Dao extends JpaRepository<User1, Long> {
    public List<User1> findByNickname(String nickname);
}
