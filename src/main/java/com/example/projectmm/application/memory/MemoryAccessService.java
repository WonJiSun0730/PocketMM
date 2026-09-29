package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryAccessLevel;
import com.example.projectmm.domain.memory.MemoryAccessState;
import com.example.projectmm.domain.memory.MemoryAccessStateRepository;
import com.example.projectmm.domain.memory.MemoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MemoryAccessService {

    private final MemoryRepository memoryRepository;
    private final MemoryAccessStateRepository memoryAccessStateRepository;
    private final MemoryLifecycleService memoryLifecycleService;

    @Transactional
    public List<MemoryAccessState> changeAccessForConversation(Long characterId, Long roomId,
            LocalDateTime since, MemoryAccessLevel level, LocalDateTime at) {
        if (characterId == null || roomId == null || level == null || at == null)
            throw new IllegalArgumentException("character, conversation, level and time are required");
        var recovered = new java.util.ArrayList<Long>();
        var results = memoryRepository.findForConversation(characterId, roomId).stream()
                .filter(memory -> memory.getConversation() != null && roomId.equals(memory.getConversation().getId()))
                .filter(memory -> !memory.getRememberedAt().isAfter(at))
                .filter(memory -> since == null || !memory.getRememberedAt().isBefore(since))
                .map(memory -> changeAccessForMemory(memory, level, at, recovered)).toList();
        markRecoveredMemoriesAccessed(recovered, at);
        return results;
    }

    @Transactional
    public MemoryAccessState changeAccess(
            Long memoryId,
            MemoryAccessLevel accessLevel,
            LocalDateTime currentTime
    ) {
        if (memoryId == null) {
            throw new IllegalArgumentException(
                    "memoryId must not be null"
            );
        }

        if (accessLevel == null) {
            throw new IllegalArgumentException(
                    "accessLevel must not be null"
            );
        }

        if (currentTime == null) {
            throw new IllegalArgumentException(
                    "currentTime must not be null"
            );
        }

        Memory memory =
                memoryRepository.findById(memoryId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Memory not found: "
                                                + memoryId
                                )
                        );

        MemoryAccessState accessState =
                memoryAccessStateRepository
                        .findByMemoryId(memoryId)
                        .orElseGet(
                                () -> new MemoryAccessState(
                                        memory,
                                        accessLevel,
                                        currentTime
                                )
                        );

        MemoryAccessLevel previousLevel =
                accessState.getAccessLevel();

        if (previousLevel != accessLevel) {

            accessState.changeAccessLevel(
                    accessLevel,
                    currentTime
            );

            if (accessLevel == MemoryAccessLevel.EXPLICIT
                    && previousLevel != MemoryAccessLevel.EXPLICIT) {

                memoryLifecycleService.markAccessed(
                        List.of(memoryId),
                        currentTime
                );
            }
        }

        return memoryAccessStateRepository.save(
                accessState
        );
    }

    @Transactional
    public List<MemoryAccessState> changeAccessForCharacter(
            Long characterId,
            MemoryAccessLevel accessLevel,
            LocalDateTime currentTime
    ) {
        if (characterId == null) {
            throw new IllegalArgumentException(
                    "characterId must not be null"
            );
        }

        if (accessLevel == null) {
            throw new IllegalArgumentException(
                    "accessLevel must not be null"
            );
        }

        if (currentTime == null) {
            throw new IllegalArgumentException(
                    "currentTime must not be null"
            );
        }

        List<Memory> memories =
                memoryRepository
                        .findByCharacterIdOrderByRememberedAtDesc(
                                characterId
                        );

        List<Long> recoveredMemoryIds =
                new java.util.ArrayList<>();

        List<MemoryAccessState> results =
                memories.stream()
                        .map(memory ->
                                changeAccessForMemory(
                                        memory,
                                        accessLevel,
                                        currentTime,
                                        recoveredMemoryIds
                                )
                        )
                        .toList();

        markRecoveredMemoriesAccessed(
                recoveredMemoryIds,
                currentTime
        );

        return results;
    }

    @Transactional
    public List<MemoryAccessState> changeAccessForCharacterSince(
            Long characterId,
            LocalDateTime since,
            MemoryAccessLevel accessLevel,
            LocalDateTime currentTime
    ) {
        if (characterId == null) {
            throw new IllegalArgumentException(
                    "characterId must not be null"
            );
        }

        if (since == null) {
            throw new IllegalArgumentException(
                    "since must not be null"
            );
        }

        if (accessLevel == null) {
            throw new IllegalArgumentException(
                    "accessLevel must not be null"
            );
        }

        if (currentTime == null) {
            throw new IllegalArgumentException(
                    "currentTime must not be null"
            );
        }

        List<Memory> memories =
                memoryRepository
                        .findByCharacterIdOrderByRememberedAtDesc(
                                characterId
                        );

        List<Long> recoveredMemoryIds =
                new java.util.ArrayList<>();

        List<MemoryAccessState> results =
                memories.stream()
                        .filter(memory ->
                                !memory.getRememberedAt()
                                        .isBefore(since)
                        )
                        .map(memory ->
                                changeAccessForMemory(
                                        memory,
                                        accessLevel,
                                        currentTime,
                                        recoveredMemoryIds
                                )
                        )
                        .toList();

        markRecoveredMemoriesAccessed(
                recoveredMemoryIds,
                currentTime
        );

        return results;
    }

    private MemoryAccessState changeAccessForMemory(
            Memory memory,
            MemoryAccessLevel accessLevel,
            LocalDateTime currentTime,
            List<Long> recoveredMemoryIds
    ) {
        MemoryAccessState accessState =
                memoryAccessStateRepository
                        .findByMemoryId(
                                memory.getId()
                        )
                        .orElseGet(
                                () -> new MemoryAccessState(
                                        memory,
                                        accessLevel,
                                        currentTime
                                )
                        );

        MemoryAccessLevel previousLevel =
                accessState.getAccessLevel();

        if (previousLevel != accessLevel) {

            accessState.changeAccessLevel(
                    accessLevel,
                    currentTime
            );

            if (accessLevel == MemoryAccessLevel.EXPLICIT
                    && previousLevel != MemoryAccessLevel.EXPLICIT) {

                recoveredMemoryIds.add(
                        memory.getId()
                );
            }
        }

        return memoryAccessStateRepository.save(
                accessState
        );
    }

    private void markRecoveredMemoriesAccessed(
            List<Long> recoveredMemoryIds,
            LocalDateTime currentTime
    ) {
        if (recoveredMemoryIds.isEmpty()) {
            return;
        }

        memoryLifecycleService.markAccessed(
                recoveredMemoryIds,
                currentTime
        );
    }
}
