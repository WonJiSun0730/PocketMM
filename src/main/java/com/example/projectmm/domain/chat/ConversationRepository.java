package com.example.projectmm.domain.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository
        extends JpaRepository<Conversation, Long> {

    // transaction 밖 beginChat resolve에서 context의 캐릭터 이름을 안전하게 읽는다.
    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "character")
    Optional<Conversation> findById(Long id);

    List<Conversation>
    findByCharacterIdOrderByCreatedAtDesc(Long characterId);

    Optional<Conversation>
    findTopByCharacterIdOrderByCreatedAtDesc(Long characterId);

    // 동일한 생성 시각이어도 마지막 생성된 방을 확실하게 선택한다.
    Optional<Conversation> findTopByCharacterIdOrderByCreatedAtDescIdDesc(Long characterId);

    Optional<Conversation> findTopByCharacterIdAndWorldIdOrderByCreatedAtDescIdDesc(
            Long characterId,
            Long worldId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id")
    Optional<Conversation> lockForMemory(@Param("id") Long id);
}
