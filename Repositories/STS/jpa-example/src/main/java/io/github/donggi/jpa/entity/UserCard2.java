package io.github.donggi.jpa.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity(name = "jpa_user_card2")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCard2 {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userCardId;
    private Long cardId;

    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "userId")
    private UserWithCard1 owner;
}