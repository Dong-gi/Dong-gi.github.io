package io.github.donggi.jpa.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.GachaWithOdds;

@Repository
public interface GachaWithOddsDao extends JpaRepository<GachaWithOdds, Long> {}
