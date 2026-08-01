package io.github.donggi.jpa.entity;

import java.util.Date;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

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