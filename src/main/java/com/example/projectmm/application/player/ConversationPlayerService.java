package com.example.projectmm.application.player;

import com.example.projectmm.application.state.CustomStateService;
import com.example.projectmm.domain.chat.*;
import com.example.projectmm.domain.player.PlayerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ConversationPlayerService {
    private final ConversationRepository conversations;
    private final PlayerProfileRepository profiles;
    private final CustomStateService customStateService;

    public record Session(Long conversationId, PlayerProfileResponse playerProfile) {}

    @Transactional(readOnly = true)
    public Session current(Long characterId) {
        var room = conversations.findTopByCharacterIdOrderByCreatedAtDescIdDesc(characterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return response(room);
    }

    @Transactional
    public Session select(Long characterId, Long conversationId, Long profileId) {
        var room = conversations.lockForMemory(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!room.getCharacter().getId().equals(characterId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var profile = profiles.findById(profileId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // 선택은 이 방에만 저장하며 다른 방의 프로필 선택에는 영향을 주지 않는다.
        room.selectPlayerProfile(profile);

        // 선택된 프로필의 상태 정의 중 이 방에 아직 값이 없는 항목만 기본값으로 초기화한다.
        customStateService.initializeSelectedProfile(room);

        return response(room);
    }

    private Session response(Conversation room) {
        return new Session(room.getId(), room.getPlayerProfile() == null ? null : PlayerProfileResponse.from(room.getPlayerProfile()));
    }
}
