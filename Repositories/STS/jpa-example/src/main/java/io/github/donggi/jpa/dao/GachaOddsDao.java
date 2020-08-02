package io.github.donggi.jpa.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.GachaOdds;

@Repository
public interface GachaOddsDao extends JpaRepository<GachaOdds, Long> {
    @Query("select new io.github.donggi.jpa.entity.GachaOdds$Minimal(id.seqNo, objectId, odd) from jpa_gacha_odds where id.gachaId = :gachaId")
    public List<GachaOdds.Minimal> findMinimalOdds(@Param("gachaId") Long gachaId);
}
