package com.example.projectmm.api.world;
import java.util.List;
public record WorldSystemItem(String name, String changeCondition, int minValue, int maxValue, List<WorldSystemStateItem> states) {}
