package io.github.donggi.jpa.entity;

import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity(name = "jpa_user2")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User2 {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;
    private String nickname;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(insertable = false, updatable = false)
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