package com.example.projectmm.application.qa;

import com.example.projectmm.application.chat.WorldRoomSessionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.List;

public class PocketExperienceSnapshotService {
    private final WorldRoomSessionService sessions;private final JdbcTemplate jdbc;private final Environment env;
    public PocketExperienceSnapshotService(WorldRoomSessionService sessions,JdbcTemplate jdbc,Environment env){this.sessions=sessions;this.jdbc=jdbc;this.env=env;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PocketExperienceSnapshot capture(long id,String owner) {
        var pocket=sessions.pocket(id,owner);
        if(pocket.progressInProgress(java.time.LocalDateTime.now()))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"부재 활동 정리가 끝난 뒤 테스트를 시작해 주세요."); // Authorize before reading any table. Never call contextBuilder on the source: recall mutates access state.
        var tables=PocketExperienceSnapshot.readTables(jdbc,id);
        return new PocketExperienceSnapshot(id,Instant.now().toString(),PocketExperienceSnapshot.digest(tables),"OPENAI",
                env.getProperty("openai.memory-model","gpt-5.6-luna"),
                env.getProperty("openai.compression-model","gpt-5.6-luna"),
                env.getProperty("openai.time-model","gpt-5.6-luna"),
                env.getProperty("openai.embedding-model","text-embedding-3-small"),
                env.getProperty("chat.memory.enabled",Boolean.class,true),tables,
                List.of("테스트 저장소는 별도 H2 MySQL 모드로 동시성·DB 동작은 운영 DB와 다를 수 있음","테스트 전용 네트워크 시간 제한 및 하드캡 적용; 역할별 출력 상한은 테스트에만 적용하며 서비스 프롬프트는 유지","원본은 읽기 전용이며 계정 인증정보는 복사하지 않음","격리 환경에서 부재 진행과 백그라운드 작업은 실행하지 않음","선택한 대상 OpenAI 모델 및 OpenAI 텍스트 보조 호출로 실행; 서비스의 응답 길이·문체 지침은 유지","사용자만 AI로 대체; 플레이어 AI는 전체 대화와 플레이어 설정을 읽으며 캐릭터의 비공개 기억은 읽지 않음","서비스 연속성 검사 호출은 비용에 포함되며 최초 사실 초기화·수정 시 추가 호출이 발생할 수 있음","별도 평가 모델 호출 없음; 자동 품질 표시는 검토 후보이며 의미적 합격 판정이 아님"));
    }
}
