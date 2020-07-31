package io.github.donggi.jpa.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.Item5;

@Repository
public interface Item5Dao extends JpaRepository<Item5, Long> {}
