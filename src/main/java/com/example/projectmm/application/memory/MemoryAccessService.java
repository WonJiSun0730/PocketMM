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

        if (accessState.getAccessLevel()
                != accessLevel) {

            accessState.changeAccessLevel(
                    accessLevel,
                    currentTime
            );
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

        return memories.stream()
                .map(memory ->
                        changeAccessForMemory(
                                memory,
                                accessLevel,
                                currentTime
                        )
                )
                .toList();
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

        return memories.stream()
                .filter(memory ->
                        !memory.getRememberedAt()
                                .isBefore(since)
                )
                .map(memory ->
                        changeAccessForMemory(
                                memory,
                                accessLevel,
                                currentTime
                        )
                )
                .toList();
    }

    private MemoryAccessState changeAccessForMemory(
            Memory memory,
            MemoryAccessLevel accessLevel,
            LocalDateTime currentTime
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

        if (accessState.getAccessLevel()
                != accessLevel) {

            accessState.changeAccessLevel(
                    accessLevel,
                    currentTime
            );
        }

        return memoryAccessStateRepository.save(
                accessState
        );
    }
}