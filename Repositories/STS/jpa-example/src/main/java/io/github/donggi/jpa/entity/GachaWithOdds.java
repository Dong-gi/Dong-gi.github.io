package io.github.donggi.jpa.entity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity(name = "jpa_gacha")
@Getter
@Setter
@NoArgsConstructor
public class GachaWithOdds extends AbstractGacha {
    @ElementCollection
    @CollectionTable(
            name = "jpa_gacha_odds",
            joinColumns = {@JoinColumn(name = "gachaId")})
    @Column(name = "objectId")
    private Collection<Long> objectIds;

    @ElementCollection
    @CollectionTable(
            name = "jpa_gacha_odds",
            joinColumns = {@JoinColumn(name = "gachaId")})
    @Column(name = "objectId")
    private List<Long> objectIdList;

    @ElementCollection
    @CollectionTable(
            name = "jpa_gacha_odds",
            joinColumns = {@JoinColumn(name = "gachaId")})
    @Column(name = "objectId")
    private Set<Long> objectIdSet;

    @ElementCollection
    @CollectionTable(
            name = "jpa_gacha_odds",
            joinColumns = {@JoinColumn(name = "gachaId")})
    @MapKeyColumn(name = "seqNo")
    @Column(name = "objectId")
    private Map<Integer, Long> objectIdMap;

    @ElementCollection
    @CollectionTable(
            name = "jpa_gacha_odds",
            joinColumns = {@JoinColumn(name = "gachaId")})
    private List<GachaOdds.Minimal> minimalOddList;
}
