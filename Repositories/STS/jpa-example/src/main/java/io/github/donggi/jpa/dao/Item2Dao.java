package io.github.donggi.jpa.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.Item2;

@Repository
public interface Item2Dao extends JpaRepository<Item2, Long> {}
