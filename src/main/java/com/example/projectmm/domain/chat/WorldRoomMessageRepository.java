package com.example.projectmm.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorldRoomMessageRepository extends JpaRepository<WorldRoomMessage, Long> {

    interface WorldTurnCount { Long getWorldId(); Long getTurns(); }

    @org.springframework.data.jpa.repository.Query("select m.session.room.world.id as worldId, count(m) as turns from WorldRoomMessage m where m.session.room.world.id in :worldIds and m.role = com.example.projectmm.domain.chat.ChatRole.CHARACTER and m.absenceReportId is null group by m.session.room.world.id")
    List<WorldTurnCount> countTurnsByWorldIds(@org.springframework.data.repository.query.Param("worldIds") List<Long> worldIds);

    @org.springframework.data.jpa.repository.Query("select m from WorldRoomMessage m where m.session.id = :sessionId "
            + "and m.id > :afterId and m.id < :beforeId order by m.id asc")
    List<WorldRoomMessage> sceneHistoryAfter(
            @org.springframework.data.repository.query.Param("sessionId") Long sessionId,
            @org.springframework.data.repository.query.Param("afterId") long afterId,
            @org.springframework.data.repository.query.Param("beforeId") long beforeId,
            org.springframework.data.domain.Pageable page);

    java.util.Optional<WorldRoomMessage> findTopBySessionIdOrderByIdDesc(Long sessionId);

    List<WorldRoomMessage> findTop50BySessionIdOrderByCreatedAtDescIdDesc(Long sessionId);

    List<WorldRoomMessage> findTop50BySessionIdOrderByIdDesc(Long sessionId);

    List<WorldRoomMessage> findTop15BySessionIdOrderByIdDesc(Long sessionId);

    List<WorldRoomMessage> findTop15BySessionIdAndIdLessThanOrderByIdDesc(Long sessionId, Long beforeId);

    List<WorldRoomMessage> findTop50BySessionIdAndIdLessThanOrderByIdDesc(Long sessionId, Long beforeId);

    List<WorldRoomMessage> findBySessionIdAndRoleAndIdLessThanOrderByIdDesc(
            Long sessionId, ChatRole role, Long beforeId, org.springframework.data.domain.Pageable page);

    List<WorldRoomMessage> findBySessionIdAndIdGreaterThanEqualAndIdLessThanOrderByIdAsc(
            Long sessionId, Long firstId, Long beforeId);

    void deleteBySessionIdAndIdGreaterThanEqual(Long sessionId, Long messageId);

    boolean existsBySessionIdAndIdGreaterThanAndAbsenceReportIdIsNull(Long sessionId, Long messageId);

    boolean existsBySessionIdAndIdGreaterThan(Long sessionId, Long messageId);

    java.util.Optional<WorldRoomMessage> findTopBySessionIdAndIdGreaterThanEqualAndRoleOrderByIdAsc(
            Long sessionId,
            Long messageId,
            ChatRole role
    );

    java.util.Optional<WorldRoomMessage> findTopBySessionIdAndIdLessThanOrderByIdDesc(Long sessionId, Long messageId);
    void deleteBySessionRoomId(Long roomId);

    void deleteBySessionId(Long sessionId);
}
