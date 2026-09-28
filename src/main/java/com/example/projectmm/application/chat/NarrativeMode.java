package com.example.projectmm.application.chat;

/**
 * 현재 narrative runtime이 어떤 실행 경로를 사용해야 하는지 나타낸다.
 *
 * LIVE_DIALOGUE:
 * 사용자의 현재 입력에 즉시 응답하는 일반 대화.
 *
 * CATCH_UP:
 * 사용자의 부재 시간 동안 발생한 진행을 먼저 반영해야 하는 상태.
 *
 * AUTO_PROGRESS:
 * 사용자가 접속하지 않은 동안 능동적으로 진행되는 미래 확장용 모드.
 * 현재 runtime에서는 아직 실행 경로를 연결하지 않는다.
 */
public enum NarrativeMode {

    LIVE_DIALOGUE,

    CATCH_UP,

    AUTO_PROGRESS
}