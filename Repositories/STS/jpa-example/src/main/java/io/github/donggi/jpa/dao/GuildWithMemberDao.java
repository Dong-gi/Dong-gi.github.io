package io.github.donggi.jpa.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.donggi.jpa.entity.GuildWithMember;

@Repository
public interface GuildWithMemberDao extends JpaRepository<GuildWithMember, Long> {}
