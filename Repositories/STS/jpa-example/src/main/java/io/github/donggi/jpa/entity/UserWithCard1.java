package io.github.donggi.jpa.entity;

import javax.persistence.Entity;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "jpa_user3")
@Data
@EqualsAndHashCode(callSuper=true)
@NoArgsConstructor
@AllArgsConstructor
public class UserWithCard1 extends AbstractUser3 {
    @OneToOne(mappedBy = "owner")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private UserCard2 card;
}