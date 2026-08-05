package io.github.donggi.jpa.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.User2;

@Repository
public interface User2Dao extends JpaRepository<User2, Long> {
    public List<User2> findByNickname(String nickname);
}
