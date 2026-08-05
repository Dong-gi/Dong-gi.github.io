package io.github.donggi.jpa.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.ItemInfo3;

@Repository
public interface ItemInfo3Dao extends JpaRepository<ItemInfo3, Long> {}
