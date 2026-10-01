package com.example.projectmm.api.world;

import com.example.projectmm.domain.chat.WorldRoomMessageVariant;

import java.time.LocalDateTime;

public record WorldRoomMessageVariantItem(
        Long id,
        int variantNo,
        String content,
        LocalDateTime createdAt,
        boolean selected
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
                selectedVariantNo != null && selectedVariantNo == variant.getVariantNo()
        );
    }
}
