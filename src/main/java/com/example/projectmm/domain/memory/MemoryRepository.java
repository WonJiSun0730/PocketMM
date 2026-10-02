package com.example.projectmm.domain.memory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface MemoryRepository
        extends JpaRepository<Memory, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Memory m where m.id = :id")
    Optional<Memory> lockForCompression(@Param("id") Long id);

    List<Memory> findByCharacterIdOrderByRememberedAtDesc(
            Long characterId
    );

    boolean existsByChatFingerprint(String chatFingerprint);

    List<Memory> findTop5ByWorldRoomSessionIdAndCharacterIdOrderByRememberedAtDescIdDesc(
            Long sessionId,
            Long characterId
    );

    List<Memory> findByWorldRoomSessionIdOrderByRememberedAtDescIdDesc(Long sessionId);

    List<Memory> findByWorldRoomSessionIdAndRememberedAtOrderByCharacterIdAscIdAsc(
            Long sessionId,
            java.time.LocalDateTime rememberedAt
    );

    List<Memory> findByWorldRoomSessionIdAndSourceWorldRoomMessageIdGreaterThanEqual(
            Long sessionId,
            Long messageId
    );

    @Query("select m from Memory m where m.character.id = :characterId "
            + "and (m.conversation is null or m.conversation.id = :conversationId) "
            + "order by m.rememberedAt desc")
    List<Memory> findForConversation(@Param("characterId") Long characterId,
                                     @Param("conversationId") Long conversationId);
}
