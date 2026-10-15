package com.example.projectmm.api.world;

import com.example.projectmm.domain.chat.WorldRoomMessageVariant;

import java.time.LocalDateTime;

public record WorldRoomMessageVariantItem(
        Long id,
        int variantNo,
        String content,
        LocalDateTime createdAt,
        boolean selected,
        LocalDateTime sceneSimulatedAt,
        String scenePlayerStateJson,
        String testDiagnosticsJson,
        String testMemoryBeforeJson,
        String testMemoryAfterJson
) {
    public static WorldRoomMessageVariantItem from(
            WorldRoomMessageVariant variant,
            Integer selectedVariantNo
    ) {
        return new WorldRoomMessageVariantItem(
                variant.getId(),
                variant.getVariantNo(),
                variant.getContent(),
                variant.getCreatedAt(),
                selectedVariantNo != null && selectedVariantNo == variant.getVariantNo(),
                variant.getAfterSimulatedAt(),
                variant.getAfterPlayerStateJson(),
                variant.getTestDiagnosticsJson(),
                variant.getTestMemoryBeforeJson(),
                variant.getTestMemoryAfterJson()
        );
    }
}
