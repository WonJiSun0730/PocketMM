package com.example.projectmm.application.relationship;

import com.example.projectmm.domain.character.Character;
import com.example.projectmm.domain.chat.ChatMessage;
import com.example.projectmm.domain.event.Event;
import com.example.projectmm.domain.relationship.RelationshipFact;
import com.example.projectmm.domain.relationship.RelationshipFactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RelationshipFactService {

    private final RelationshipFactRepository relationshipFactRepository;

    @Transactional
    public RelationshipFact establish(
            Character fromCharacter,
            Character toCharacter,
            String relation,
            String value,
            Event sourceEvent,
            LocalDateTime establishedAt
    ) {
        if (relation == null || relation.isBlank()) {
            throw new IllegalArgumentException(
                    "relation is required"
            );
        }

        String normalizedRelation =
                relation.strip();

        var existing =
                relationshipFactRepository
                        .findByFromCharacterIdAndToCharacterIdAndRelation(
                                fromCharacter.getId(),
                                toCharacter.getId(),
                                normalizedRelation
                        );

        if (existing.isPresent()) {
            RelationshipFact fact =
                    existing.get();

            fact.update(
                    value,
                    sourceEvent,
                    establishedAt
            );

            return fact;
        }

        RelationshipFact fact =
                new RelationshipFact(
                        fromCharacter,
                        toCharacter,
                        normalizedRelation,
                        value,
                        sourceEvent,
                        establishedAt
                );

        return relationshipFactRepository.save(fact);
    }

    @Transactional
    public RelationshipFact establishFromChatMessage(
            Character fromCharacter,
            Character toCharacter,
            String relation,
            String value,
            ChatMessage sourceChatMessage,
            LocalDateTime establishedAt
    ) {
        if (relation == null || relation.isBlank()) {
            throw new IllegalArgumentException(
                    "relation is required"
            );
        }

        if (sourceChatMessage == null) {
            throw new IllegalArgumentException(
                    "sourceChatMessage is required"
            );
        }

        String normalizedRelation =
                relation.strip();

        var existing =
                relationshipFactRepository
                        .findByFromCharacterIdAndToCharacterIdAndRelation(
                                fromCharacter.getId(),
                                toCharacter.getId(),
                                normalizedRelation
                        );

        if (existing.isPresent()) {
            RelationshipFact fact =
                    existing.get();

            fact.updateFromChatMessage(
                    value,
                    sourceChatMessage,
                    establishedAt
            );

            return fact;
        }

        RelationshipFact fact =
                RelationshipFact.fromChatMessage(
                        fromCharacter,
                        toCharacter,
                        normalizedRelation,
                        value,
                        sourceChatMessage,
                        establishedAt
                );

        return relationshipFactRepository.save(fact);
    }
}