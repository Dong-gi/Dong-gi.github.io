package io.github.donggi.jpa.entity;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity(name = "jpa_guild")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuildWithMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long guildId;
    private String guildName;
    
    @OneToMany
    @JoinTable(
            name = "jpa_guild_member",
            joinColumns = @JoinColumn(name = "guild_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private List<User2> members = new ArrayList<>();
}