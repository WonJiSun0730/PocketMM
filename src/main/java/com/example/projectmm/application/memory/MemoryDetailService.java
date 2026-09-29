package com.example.projectmm.application.memory;

import com.example.projectmm.domain.memory.Memory;
import com.example.projectmm.domain.memory.MemoryDetailSalience;
import com.example.projectmm.domain.memory.ProtectedMemoryDetail;
import com.example.projectmm.domain.memory.ProtectedMemoryDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MemoryDetailService {

    private final ProtectedMemoryDetailRepository details;

    public List<ProtectedMemoryDetail> saveDetails(
            Memory memory,
            List<MemoryDetailCandidate> candidates
    ) {
        if (memory == null || memory.getId() == null
                || candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        Map<String, MemoryDetailCandidate> unique =
                new LinkedHashMap<>();

        for (MemoryDetailCandidate candidate : candidates) {

            if (candidate == null
                    || candidate.text() == null
                    || candidate.text().isBlank()
                    || candidate.salience() == null) {
                continue;
            }

            String text = candidate.text().strip();

            if (text.length() > 300) {
                continue;
            }

            MemoryDetailCandidate previous =
                    unique.get(text);

            if (previous == null) {
                unique.put(
                        text,
                        new MemoryDetailCandidate(
                                text,
                                candidate.salience(),
                                candidate.protectedAnchor()
                        )
                );

                continue;
            }

            MemoryDetailSalience mergedSalience =
                    candidate.salience().weight()
                            > previous.salience().weight()
                            ? candidate.salience()
                            : previous.salience();

            boolean mergedProtectedAnchor =
                    previous.protectedAnchor()
                            || candidate.protectedAnchor();

            unique.put(
                    text,
                    new MemoryDetailCandidate(
                            text,
                            mergedSalience,
                            mergedProtectedAnchor
                    )
            );
        }

        return unique.values()
                .stream()
                .map(candidate ->
                        new ProtectedMemoryDetail(
                                memory,
                                candidate.text(),
                                candidate.salience(),
                                candidate.protectedAnchor()
                        )
                )
                .filter(ProtectedMemoryDetail::mustPreserve)
                .map(details::save)
                .toList();
    }
}