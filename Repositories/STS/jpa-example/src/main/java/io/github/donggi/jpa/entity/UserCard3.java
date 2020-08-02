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

@Entity(name = "jpa_user_card3")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCard3 {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userCardId;
    private Long cardId;
    
    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "userId")
    private User3 owner;
}