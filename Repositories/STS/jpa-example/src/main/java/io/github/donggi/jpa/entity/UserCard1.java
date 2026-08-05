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

@Entity(name = "jpa_user_card1")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCard1 {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userCardId;
    //private Long userId; // 조인 컬럼은 자동으로 구성하므로 별도로 가지면 안 됨.
    private Long cardId;

    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "userId")
    private User3 owner;
}