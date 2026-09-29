package com.example.projectmm.application.simulation;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.scene.Scene;
import com.example.projectmm.domain.scene.SceneParticipant;
import com.example.projectmm.domain.scene.SceneParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CatchUpSceneResolver {

    private final SceneParticipantRepository sceneParticipantRepository;
    private final CatchUpSceneFactory catchUpSceneFactory;

    // A room catch-up must not reuse an unrelated active world scene.
    public Scene createForConversation(
            Conversation conversation,
            Character character,
            LocalDateTime startedAt
    ) {
        return catchUpSceneFactory.createForConversation(
                conversation,
                character,
                startedAt
        );
    }

    public Scene resolveOrCreate(
            Character character,
            LocalDateTime startedAt
    ) {
        List<SceneParticipant> participants =
                sceneParticipantRepository
                        .findByCharacterIdAndSceneActiveTrue(
                                character.getId()
                        );

        Optional<Scene> existingScene =
                participants.stream()
                        .map(SceneParticipant::getScene)
                        .filter(scene ->
                                "DAILY".equals(scene.getSceneType())
                        )
                        .max(
                                Comparator.comparing(
                                        Scene::getStartedAt
                                )
                        );

        return existingScene.orElseGet(
                () -> catchUpSceneFactory.create(
                        character,
                        startedAt
                )
        );
    }
}
