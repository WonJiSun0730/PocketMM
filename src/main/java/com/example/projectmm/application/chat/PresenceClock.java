package com.example.projectmm.application.chat;

import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class PresenceClock {
    // DB 정밀도에 맞추고, 테스트에서는 실제 대기 없이 접속 시각을 고정한다.
    public LocalDateTime now() { return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS); }
}
