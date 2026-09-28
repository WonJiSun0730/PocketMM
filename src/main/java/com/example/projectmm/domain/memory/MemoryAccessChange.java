package com.example.projectmm.domain.memory;

// AI 제안의 원시 값은 validator에서 검사한다. 변경 대상 ID는 받지 않는다.
public record MemoryAccessChange(String scope, String accessLevel, String since) {
}
