package com.example.projectmm.domain.dialogue;

public record DialogueCustomState(String name, double currentValue, double minValue, double maxValue,
                                  String unit, String description) {
}
