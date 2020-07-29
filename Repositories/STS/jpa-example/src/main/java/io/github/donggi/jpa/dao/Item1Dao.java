package io.github.donggi.jpa.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.Item1;

@Repository
public interface Item1Dao extends JpaRepository<Item1, Long> {}
