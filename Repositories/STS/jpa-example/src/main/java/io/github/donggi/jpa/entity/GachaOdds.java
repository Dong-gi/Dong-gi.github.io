package io.github.donggi.jpa.entity;

import java.io.Serializable;

import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity(name = "jpa_gacha_odds")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GachaOdds {    
    @Id
    private ID id;
    private String memo;
    private Long objectId;
    private Integer odd;
    
    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor    
    public static class ID implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long gachaId;
        private Integer seqNo;
    }
    
    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor    
    public static class Minimal {
        private Integer seqNo;
        private Long objectId;
        private Integer odd;
    }
}
