package com.example.projectmm.domain.memory;

public enum MemoryAccessLevel {

    EXPLICIT,
    IMPLICIT,
    RESIDUAL,
    BLOCKED
}

//EXPLICIT: 사건 내용을 명시적으로 떠올릴 수 있음
//IMPLICIT: 구체적 사건은 못 떠올리지만 사람/말투/습관 등에 익숙함
//RESIDUAL: 내용과 친숙함보다 감정적 흔적 위주로 남음
//BLOCKED: 현재 접근 불가