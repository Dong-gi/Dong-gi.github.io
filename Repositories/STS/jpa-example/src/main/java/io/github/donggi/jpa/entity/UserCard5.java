package io.github.donggi.jpa.entity;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity(name = "jpa_user_card5")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCard5 {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userCardId;
    private Long userId;
    private Long cardId;
}