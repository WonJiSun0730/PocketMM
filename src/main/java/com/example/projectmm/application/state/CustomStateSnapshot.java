package com.example.projectmm.application.state;

import com.example.projectmm.domain.state.CustomStateValue;

public record CustomStateSnapshot(Long definitionId, String name, double currentValue,
                                  double minValue, double maxValue, String unit, String description) {
    public static CustomStateSnapshot from(CustomStateValue value) {
        var definition = value.getDefinition();
        return new CustomStateSnapshot(definition.getId(), definition.getName(), value.getCurrentValue(),
                definition.getMinValue(), definition.getMaxValue(), definition.getUnit(), definition.getDescription());
    }
}
