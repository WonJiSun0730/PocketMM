package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.MemoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;

@Service
@RequiredArgsConstructor
public class MemoryLifecycleService {

    private final MemoryRepository memoryRepository;

    private static final long REACTIVATION_GAP_DAYS = 30;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAccessed(
            Collection<Long> memoryIds,
            LocalDateTime accessedAt
    ) {
        if (memoryIds == null || memoryIds.isEmpty() || accessedAt == null) {
            return;
        }

        memoryRepository.findAllById(memoryIds)
                .forEach(memory -> {

                    LocalDateTime previousAccess =
                            memory.getLastAccessedAt();

                    if (previousAccess != null
                            && Duration.between(
                            previousAccess,
                            accessedAt
                    ).toDays() >= REACTIVATION_GAP_DAYS) {

                        memory.reactivate(accessedAt);
                        return;
                    }

                    memory.markAccessed(accessedAt);
                });
    }
}