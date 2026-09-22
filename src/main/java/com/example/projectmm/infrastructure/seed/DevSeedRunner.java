package com.example.projectmm.infrastructure.seed;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.character.CharacterRepository;
import com.example.projectmm.domain.character.CharacterState;
import com.example.projectmm.domain.character.CharacterStateRepository;
import com.example.projectmm.domain.event.Event;
import com.example.projectmm.domain.event.EventParticipant;
import com.example.projectmm.domain.event.EventParticipantRepository;
import com.example.projectmm.domain.event.EventRepository;
import com.example.projectmm.domain.scene.Scene;
import com.example.projectmm.domain.scene.SceneParticipant;
import com.example.projectmm.domain.scene.SceneParticipantRepository;
import com.example.projectmm.domain.scene.SceneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@Profile("local")
@Order(1)
@RequiredArgsConstructor
public class DevSeedRunner implements CommandLineRunner {

    private final CharacterRepository characterRepository;
    private final SceneRepository sceneRepository;
    private final CharacterStateRepository characterStateRepository;
    private final SceneParticipantRepository sceneParticipantRepository;
    private final EventRepository eventRepository;
    private final EventParticipantRepository eventParticipantRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (characterRepository.existsByName("Millie")
                || characterRepository.existsByName("Marbas")) {
            return;
        }

        Character millie =
                characterRepository.save(new Character("Millie"));

        Character marbas =
                characterRepository.save(new Character("Marbas"));

        CharacterState millieState =
                new CharacterState(
                        millie,
                        30,
                        70,
                        LocalDateTime.now().minusHours(18)
                );

        CharacterState marbasState =
                new CharacterState(
                        marbas,
                        70,
                        70,
                        LocalDateTime.now().minusHours(18)
                );

        characterStateRepository.save(millieState);
        characterStateRepository.save(marbasState);

        LocalDateTime now = LocalDateTime.now();

        Scene scene = sceneRepository.save(
                new Scene(
                        now,
                        "평범한 오후",
                        "DAILY"
                )
        );

        sceneParticipantRepository.save(
                new SceneParticipant(scene, millie)
        );

        sceneParticipantRepository.save(
                new SceneParticipant(scene, marbas)
        );

        Event event = eventRepository.save(
                new Event(
                        scene,
                        now.plusMinutes(10),
                        "INTERACTION",
                        "밀리와 마르바스가 함께 시간을 보냈다.",
                        5
                )
        );

        eventParticipantRepository.save(
                new EventParticipant(event, millie, "ACTOR")
        );

        eventParticipantRepository.save(
                new EventParticipant(event, marbas, "PARTICIPANT")
        );
    }
}