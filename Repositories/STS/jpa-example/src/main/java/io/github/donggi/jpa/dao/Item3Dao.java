package io.github.donggi.jpa.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.Item3;

@Repository
public interface Item3Dao extends JpaRepository<Item3, Long> {}
