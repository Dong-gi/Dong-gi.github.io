package io.github.donggi.jpa.entity;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity(name = "jpa_user_card4")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCard4 {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userCardId;
    private Long cardId;
    
    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "userId")
    private UserWithCard2 owner;
}