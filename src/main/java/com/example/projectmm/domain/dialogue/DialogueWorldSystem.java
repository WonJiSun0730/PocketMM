package com.example.projectmm.domain.dialogue;

import java.util.List;

public record DialogueWorldSystem(String name, String changeCondition, int minValue, int maxValue, List<DialogueWorldSystemState> states) {}
