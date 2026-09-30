package com.example.projectmm.application.chat;

import com.example.projectmm.domain.character.CharacterRepository;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.player.PlayerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class ConversationStartService {
    private final CharacterRepository characters;
    private final ConversationRepository conversations;
    private final PlayerProfileRepository players;

    @Transactional
    public NewConversationResponse start(Long characterId) {
        return start(characterId, null);
    }

    @Transactional
    public NewConversationResponse start(Long characterId, Long playerProfileId) {
        var character = characters.findById(characterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));
        // 새 방만 생성한다. 이전 메시지·기억은 복사하거나 삭제하지 않는다.
        // DB의 마이크로초 정밀도와 응답 시각을 맞춰 즉시 저장한 기억이 미래로 오인되지 않게 한다.
        var conversation = Conversation.fresh(character, LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        if (playerProfileId != null) {
            conversation.selectPlayerProfile(players.findById(playerProfileId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player profile not found")));
        }
        conversations.save(conversation);
        return new NewConversationResponse(conversation.getId(), conversation.getSimulatedAt(), java.util.List.of());
    }
}
