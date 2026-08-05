package io.github.donggi.jpa.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.OneToMany;
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