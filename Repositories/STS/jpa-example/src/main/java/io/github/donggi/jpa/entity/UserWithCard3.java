package io.github.donggi.jpa.entity;

import java.util.List;

import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
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
public class UserWithCard3 extends AbstractUser3 {
    @OneToMany
    @JoinColumn(name = "userId")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<UserCard5> cards;
}