package io.github.donggi.jpa.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.Item4;

@Repository
public interface Item4Dao extends JpaRepository<Item4, Long> {}
