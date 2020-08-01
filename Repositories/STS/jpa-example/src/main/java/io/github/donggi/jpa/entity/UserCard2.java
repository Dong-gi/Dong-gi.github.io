package io.github.donggi.jpa.entity;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
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