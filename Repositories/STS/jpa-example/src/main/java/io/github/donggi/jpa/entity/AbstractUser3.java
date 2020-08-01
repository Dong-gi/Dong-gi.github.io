package io.github.donggi.jpa.entity;

import java.util.Date;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@MappedSuperclass
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class AbstractUser3 {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;
    private String nickname;
    @Temporal(TemporalType.TIMESTAMP)
    private Date addDate;
    @Temporal(TemporalType.TIMESTAMP)
    private Date updDate;

    @PrePersist
    public void prePersist() {
        addDate = updDate = new Date();
    }
    @PreUpdate
    public void preUpdate() {
        updDate = new Date();
    }
}