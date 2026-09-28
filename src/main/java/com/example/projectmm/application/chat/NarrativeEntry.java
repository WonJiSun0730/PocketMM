package com.example.projectmm.application.chat;

/**
 * LIVE dialogue에 진입하기 전에 어떤 narrative 처리가 수행되었는지 나타낸다.
 *
 * DIRECT:
 * 별도의 catch-up 없이 바로 LIVE dialogue에 진입했다.
 *
 * AFTER_CATCH_UP:
 * 부재 구간의 catch-up을 먼저 처리한 뒤 LIVE dialogue에 진입했다.
 */
public enum NarrativeEntry {
    DIRECT,
    AFTER_CATCH_UP
}