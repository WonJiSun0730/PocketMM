package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.ProtectedMemoryDetail;
import com.example.projectmm.domain.memory.ProtectedMemoryDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProtectedMemoryDetailPolicy {

    private final ProtectedMemoryDetailRepository details;

    public List<ProtectedMemoryDetail> protectedDetails(
            Long memoryId
    ) {
        if (memoryId == null) {
            return List.of();
        }

        return details.findByMemoryIdOrderByIdAsc(
                        memoryId
                )
                .stream()
                .filter(
                        ProtectedMemoryDetail::mustPreserve
                )
                .toList();
    }

    public boolean preservesProtectedDetails(
            Long memoryId,
            String candidateSummary
    ) {
        if (candidateSummary == null
                || candidateSummary.isBlank()) {
            return false;
        }

        return protectedDetails(memoryId)
                .stream()
                .allMatch(detail ->
                        candidateSummary.contains(
                                detail.getDetailText()
                        )
                );
    }
}