package com.example.projectmm.api.world;

import com.example.projectmm.domain.chat.WorldRoomSessionSystemState;

public record WorldRoomSessionSystemItem(
        Long systemId,
        String name,
        int currentValue,
        int minValue,
        int maxValue,
        String changeCondition
) {
    public static WorldRoomSessionSystemItem from(WorldRoomSessionSystemState state) {
        return new WorldRoomSessionSystemItem(
                state.getSystem().getId(),
                state.getSystem().getName(),
                state.getCurrentValue(),
                state.getSystem().getMinValue(),
                state.getSystem().getMaxValue(),
                state.getSystem().getChangeCondition()
        );
    }
}
