package com.example.projectmm.domain.dialogue;

// 화면에 표시할 RP 본문과 서버가 검증할 상태 제안을 분리한다.
public record DialogueTurn(String reply, DialogueStateChange stateChange) {}
