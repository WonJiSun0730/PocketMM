package com.example.projectmm.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorldRoomSessionRepository extends JpaRepository<WorldRoomSession, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from WorldRoomSession s where s.id = :id")
    Optional<WorldRoomSession> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<WorldRoomSession> findTopByRoomIdAndMemberIdOrderByCreatedAtDescIdDesc(
            Long roomId,
            Long memberId
    );

    Optional<WorldRoomSession> findByIdAndRoomIdAndMemberId(
            Long sessionId,
            Long roomId,
            Long memberId
    );
    // Legacy pockets use actual reply/variant creation times, never their fictional date or presence.
    @org.springframework.data.jpa.repository.Query("""
            select s from WorldRoomSession s where s.member.id = :memberId
            order by coalesce(s.lastProgressedAt,
                (select max(coalesce(v.createdAt, m.createdAt)) from WorldRoomMessage m
                    left join WorldRoomMessageVariant v on v.message.id = m.id
                    where m.session.id = s.id
                    and m.role = com.example.projectmm.domain.chat.ChatRole.CHARACTER)) desc,
                s.id desc
            """)
    List<WorldRoomSession> findByMemberIdOrderByProgressDesc(
            @org.springframework.data.repository.query.Param("memberId") Long memberId);
    List<WorldRoomSession> findByRoomIdOrderByCreatedAtDescIdDesc(Long roomId);
    void deleteByRoomId(Long roomId);
}
