package com.example.projectmm.domain.memory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MemoryRepository
        extends JpaRepository<Memory, Long> {

    List<Memory> findByCharacterIdOrderByRememberedAtDesc(
            Long characterId
    );

    boolean existsByChatFingerprint(String chatFingerprint);

    @Query("select m from Memory m where m.character.id = :characterId "
            + "and (m.conversation is null or m.conversation.id = :conversationId) "
            + "order by m.rememberedAt desc")
    List<Memory> findForConversation(@Param("characterId") Long characterId,
                                     @Param("conversationId") Long conversationId);
}
