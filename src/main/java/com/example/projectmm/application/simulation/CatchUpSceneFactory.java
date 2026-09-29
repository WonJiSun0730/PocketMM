package com.example.projectmm.application.simulation;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.chat.Conversation;
import com.example.projectmm.domain.scene.Scene;
import com.example.projectmm.domain.scene.SceneParticipant;
import com.example.projectmm.domain.scene.SceneParticipantRepository;
import com.example.projectmm.domain.scene.SceneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class CatchUpSceneFactory {

    private final SceneRepository sceneRepository;
    private final SceneParticipantRepository sceneParticipantRepository;

    public Scene create(
            Character character,
            LocalDateTime startedAt
    ) {
        Scene scene =
                new Scene(
                        startedAt,
                        "Catch-up 일상",
                        "DAILY"
                );

        Scene savedScene =
                sceneRepository.save(scene);

        SceneParticipant participant =
                new SceneParticipant(
                        savedScene,
                        character
                );

        sceneParticipantRepository.save(participant);

        return savedScene;
    }

    public Scene createForConversation(
            Conversation conversation,
            Character character,
            LocalDateTime startedAt
    ) {
        Scene scene =
                Scene.forConversation(
                        conversation,
                        startedAt,
                        "Catch-up 일상",
                        "DAILY"
                );

        Scene savedScene =
                sceneRepository.save(scene);

        SceneParticipant participant =
                new SceneParticipant(
                        savedScene,
                        character
                );

        sceneParticipantRepository.save(participant);

        return savedScene;
    }
}