# 모바일 입력 및 부재 처리 수정 — 2026-10-13

## 확인한 원인
- #002: 입력창이 모바일·IME 구분 없이 Enter를 전송으로 처리했다.
- #003: 최소 부재 시간이 서버 세 곳에서 60초로 고정돼 있었다.
- #004: 프론트 복귀 이벤트가 대화 작업 상태를 검사하지 않았다. 서버는 이미 세션 행 잠금과 부재 작업 lease로 적용을 직렬화했지만, 장시간 대화 도중 복귀·presence 요청이 잠금 대기/충돌을 유발할 수 있었다. 대화 시작 때만 presence를 갱신하여 생성 시간이 부재로 계산될 수도 있었다.
- #005: OFF에서도 프론트가 resume/presence를 호출했다. 서버의 OFF 생성 차단은 이미 존재했다.

## 수정
- PocketPage와 ChatPage에 공통 Enter 판단을 적용했다. 모바일(터치 기본 포인터/모바일 UA/iPad 데스크톱 UA)은 개행, PC Enter는 전송, Shift Enter와 IME 확정은 전송하지 않는다. 전송 버튼은 유지했다.
- 포켓별 최소 부재 시간을 1~4320분으로 저장한다. 기존 포켓/null은 새 기본값인 15분으로 해석한다. 시간 체크 패널에서 입력 후 저장한다. resume/presence/대화 시작 검사에 같은 기준을 사용한다.
- 답변 생성 중 자동 복귀 확인을 보류한다. 반복 복귀를 한 개로 합치고 답변 완료 후 확인한다. 부재 기능 OFF에서는 자동 resume/presence를 보내지 않는다.
- 정상 대화 작업 완료 시 presence 기준을 갱신한다. 생성 대기 시간을 이중으로 부재 시간에 가산하지 않는다. 실패 트랜잭션은 해당 갱신도 롤백한다.
- 기존 행 잠금, 부재 lease, 원자적 적용, 시간·기억·상태 스냅샷은 유지했다.

## 변경 파일
- frontend/src/lib/chatInput.js 및 chatInput.test.js
- frontend/src/pages/PocketPage.jsx, ChatPage.jsx
- frontend/src/hooks/usePocketProgress.js
- frontend/src/components/PocketProgressPanel.jsx
- src/main/java/com/example/projectmm/domain/chat/WorldRoomSession.java
- src/main/java/com/example/projectmm/api/world/PocketProgressController.java
- src/main/java/com/example/projectmm/application/chat/PocketProgressService.java
- src/main/java/com/example/projectmm/application/chat/WorldRoomSessionService.java
- src/main/java/com/example/projectmm/application/chat/WorldRoomDialogueService.java
- src/test/java/com/example/projectmm/application/chat/PocketProgressIntegrationTest.java

## 검증
- 프론트 단위 테스트 27개 통과(모바일/PC/IME 입력 포함).
- PocketProgressIntegrationTest 및 WorldRoomDialogueWorldlineIntegrationTest 총 42개 통과. 기본 15분 및 15~20분당 활동 1개 프롬프트, 임계값 저장/경계, OFF, 동시 대화/복귀, 반복 복귀, 원자적 실패/재시도, 기록 삭제·복원, 파생 기억·소지품·상태 보존 포함.
- 실제 React hook 브라우저 fixture: 답변 중 두 번 복귀해도 부재 요청 증가 없음, 완료 후 1회 요청, OFF 후 반복 복귀 요청 없음.
- 실제 PocketPage 브라우저 fixture: Android 환경 Enter 후 입력값에 개행 유지·미전송, 전송 버튼 후 사용자 메시지와 AI 응답 표시.
- 프론트 production build 통과.

## 남은 확인 및 배포
- 운영 서버 미반영. 새 minimum_absence_minutes nullable 컬럼이 추가되므로 배포 전 DB 백업 후 기존 스키마 갱신 절차를 따른다.
- 브라우저 fixture/H2 검증이며 실제 iOS/Android 키보드와 운영 MariaDB 장시간 요청에서 최종 확인이 필요하다. 서버 재시작·다른 기기 동시 접속은 프론트의 대기 상태를 공유하지 않지만 기존 DB 잠금/lease로 중복 적용을 보호한다.
- 제공된 JSONL의 볼드 문제는 이전 main 수정에 포함되어 있고 운영 미반영 상태다. 그 외 영어 일관성·모브에 캐릭터 말투 전이·불필요한 빈정거림·관계/설정 직접 설명은 별도 서사 품질 검토 대상으로 유지한다. 이번 수정에서 해당 프롬프트를 임의 변경하지 않았다.

부재 전개 속도는 모델에 15~20분당 활동 하나를 지시하고 구간에 따라 요약 개수 상한을 전달한다(20분 1개, 1시간 최대 4개, 장기 부재 최대 8개). 모델의 실제 서사 속도는 생성 테스트로 계속 확인해야 한다. 진행 시각이나 이미 저장된 요약을 임의로 잘라내지 않는다.
