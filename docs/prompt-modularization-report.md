# 시스템 지침 모듈화 및 NARRATOR 도입 결과

## 1. 작업 브랜치 및 커밋

`WonJiSun0730/PocketMM`, `review/experience-qa`. 시작 HEAD: `27ba06f94e5fcfb26d412a33eb6839b95c46f70b`.

- 단계 A: `aee8cdca3ca5a8d3973ac67c5c7e40b7a08995ff` — SYSTEM/SCENE 외부화 및 동등성 검사.
- 단계 B: 이 문서를 추가한 후속 커밋 — NARRATOR, 통합 검사, 조사·비용·평가 문서.

원격 HEAD를 확인하고 기존 커밋을 부모로 보존한다. 로컬 감사용 snapshot Git 이력은 원격에 푸시하지 않는다. 기존 미추적 설정·Surefire 임시 파일은 변경에 포함하지 않았다.

## 2. 신규·수정 파일

신규: `resources/prompts/{SYSTEM,SCENE,NARRATOR}.md`, 공통 `PromptModules.java`, `PromptMigrationEquivalenceTest`, `PromptModulesTest`, `NarratorIntegrationTest`, `PromptTokenImpactTest`, 고정 SHA-256 fixture 및 이 문서 포함 기술 문서 5개.

수정: `GeminiDialogueGateway`, `WorldRoomNarrativeStyle`, `WorldRoomContinuationPrompt`. `OpenAiDialogueGateway`는 기존 공통 빌더를 통해 적용되므로 직접 수정하지 않았다. CharacterProfile, DialogueCharacterProfile, ContextBuilder, 저장·검증·모델 라우팅·예산 구현은 그대로다.

## 3. 기존 지침 이동

Java 텍스트 블록 25개를 역할별 section으로 이동했다. 정확한 분류·원본 위치·잔류 코드 사유는 [이동 내역](prompt-module-migration.md)에 기록했다. 기존 문구의 의미·선택 조건·조합 순서를 바꾸지 않았다. 기존 표현 규칙 일부는 단계 A 동등성을 위해 SCENE에 유지했다. 새 NARRATOR가 그 규칙을 대체·삭제하지 않는다.

## 4. 책임 구분

- SYSTEM: 사실·기억·설정의 권한, 인식 경계, 공통 운영 원칙.
- SCENE: 플레이어 통제, 시간·등장인물 연속성, 자동 진행 범위, 장면 호흡과 기존 live/absence 구분.
- NARRATOR: 한국어 표현·밀도·대사와 행동의 연결. 기존 운영·진행·프로필·확정 상태보다 낮은 권한임을 명시.
- Java: 동적 상태·프로필 전달, JSON 응답 형식, provider 요청 구조, 서버 검증·저장·라우팅·예산 차단. 파일에 세션 상태를 저장하지 않는다.

## 5. 실제 조합 순서

World stable: SYSTEM world.setup → SCENE world.player-settings → SYSTEM world.context-template / world.authority → SCENE world.cast → SYSTEM world.character-output → SCENE live.pacing → strict 또는 elaborate → NARRATOR. 기존 `.formatted` 인자와 프로필·세계관 데이터 위치를 보존한다.

World dynamic: 기존 시각·시스템 값·플레이어 상태·등장인물·회상 기억. OpenAI는 stable 뒤에 기존 provider 스키마 지침을 붙이고 첫 developer 메시지의 explicit cache breakpoint를 보존한다. dynamic은 다음 developer 메시지다. Gemini는 stable+dynamic 뒤 기존 JSON 응답 지침을 붙인다.

Legacy: 기존 SYSTEM/SCENE section 순서대로 조합·동적 인자 포맷 → NARRATOR → 기존 world context. Continuation은 기존 scope별 SCENE 선택을 유지한다. 구조화 부재 계획·추출 요청은 표현 지침의 대상이 아니다.

## 6. OpenAI·Gemini 적용 경로

WorldRoomNarrativeStyle을 공유하는 `GeminiDialogueGateway.buildWorldRoomPromptParts`에 NARRATOR가 한 번 들어간다. OpenAI도 동일 빌더를 사용하므로 동일 문구를 받는다. `PocketProgressService`의 부재 서사·보고도 기존 `generateWorldRoomTurn` 경로를 사용하므로 적용되며, 사실의 근거는 기존 확정 활동 기록이다. 구조화 부재 계획 추출에는 적용하지 않는다. OpenAI가 빌더를 사용하는 것은 Gemini API 호출이 아니다. 기존 provider 선택 및 OpenAI의 legacy 미지원 동작을 바꾸지 않았다. 이미지 생성도 이번 변경 대상이 아니다.

UTF-8 classpath 로딩은 초기화 시 한 번 수행하며 누락·빈 section·중복·필수 키 누락은 실패로 처리한다. 파일명 자체를 권한으로 간주하지 않는다. 패키징 JAR에서 직접 리소스를 읽는 테스트도 포함했다.

## 7. 문학 레퍼런스·추출 기법

[조사 문서](narrator-literature-research.md)에 이효석 「메밀꽃 필 무렵」, 김유정 「동백꽃」, 이승은 「파티의 끝」의 출처·조건·실제 열람 범위를 기록했다. 공간이 동선과 대화에 미치는 영향, 생활 행동을 통한 감정 전달, 대사와 반응의 호흡을 추상화했다. 작가 문장·고유 문체를 프롬프트에 복제하지 않았다.

현대 장르 로맨스 후보의 무료 표시와 작품 목록은 확인했으나 회차 본문 접근이 실패했으므로 본문 분석을 주장하지 않는다. 현대 연인 관계 소설을 장르 로맨스로 잘못 분류하지 않았다. 이 부분은 후속 보완 대상이다.

## 8. 테스트

단계 A: 변경 전 fingerprint 캡처 테스트 1개 통과 → 외부화 후 관련 39개 통과 → 최종 필수 키 로더 검사 포함 4개 재확인 통과. 이 순서를 마친 뒤 문학 조사와 NARRATOR 활성화를 진행했다.

단계 B: 관련 42개 통과, 실패·오류 0. 실제 공통 빌더로 만든 두 provider 전송 요청을 Mock에서 검사했다. 모든 전송은 인터셉터에서 종료하며 외부 transport를 실행하지 않는다. 엄격/허용 모드, 각각 1회 호출, 동일 NARRATOR 1회 삽입, 캐릭터·기억·동적 상태, 기존 JSON 형식 및 캐시 경계를 확인했다. 고정 baseline을 재생성하지 않고 정확한 NARRATOR 블록만 제거한 기존 내용의 fingerprint를 비교한다.

전체 회귀: **875개 실행, 872개 통과, 3개 건너뜀, 실패·오류 0**, BUILD SUCCESS (11분 14초, 2026-10-16 17:49:32 KST). `QaRunnerTest` 12개에 500턴·1,000턴 모의 실행 완료 검사가 포함된다. `live` 태그는 기본 제외다. 과거 임시 가격 조사 테스트의 잔류 class `GeminiPricingReadTest`는 소스에 없으며 실행 대상에서 명시 제외했다. 건너뛴 환경 의존 검사 3개는 아래 재현 명령의 Surefire 결과로 확인할 수 있다.

배포 빌드: 오프라인 Maven `-DskipTests package` 성공 (18.277초). 실제 생성된 Spring Boot JAR의 `BOOT-INF/classes/prompts/{SYSTEM,SCENE,NARRATOR}.md`가 소스와 바이트 단위로 일치함을 별도 확인했다. 실제 API 호출은 실행하지 않았다.

재현 명령:

```powershell
mvn -o '-Dtest=PromptMigrationEquivalenceTest,PromptModulesTest,NarratorIntegrationTest,PromptTokenImpactTest,*PromptTest,OpenAiModelRoutingTest,GeminiWorldRoomResponseTest,TextProviderRoutingTest,ExperienceBudgetTest' test
mvn -o '-Dtest=*,!GeminiPricingReadTest' test
mvn -o -DskipTests package
```

## 9. 토큰 증가·예상 비용

[측정 및 산식](prompt-token-cost-impact.md): 외부화 자체 변화 0, NARRATOR 추가 약 734 입력 토큰(o200k_base 합성 fixture), dynamic 변화 0, 추가 API 호출 0. 기존 luna 입력 단가 기준 cold 증가 약 $0.0001468/회. 출력·캐시 쓰기·보관·모델별 실제 청구 차이는 별도다. 실제 지출 0, 기존 누적 $4 장부와 하드캡은 초기화하지 않았다.

## 10. 기존 동작과 달라진 부분

정적 지침을 수정하려면 Markdown section을 편집하고 재빌드·재시작한다. 잘못된 리소스는 조용히 생략하지 않고 초기화 실패로 드러난다. NARRATOR의 표현 지침이 활성화되어 입력 길이·stable 해시가 변한다. 목표는 문장 길이가 아닌 표현의 질이며 실제 출력·분량은 모델에 따라 달라질 수 있다. 서버 기능·권한·저장·라우팅은 변경하지 않았다.

## 11. 미검증 범위

실제 모델의 한국어 문학적 개선, provider별 실제 청구 토큰·출력 길이·지연·캐시 적중률, 배포된 서비스의 동작은 이번 무료 테스트로 확정하지 않는다. 장르 로맨스 회차 본문 연구도 미완료다. 현재 성과는 구현과 구성·회귀 검증이며 문학적 품질 향상을 확정한 결과가 아니다.

## 12. 후속 개선

[품질 비교 시나리오](narrator-quality-scenarios.md)의 6가지 입력·10개 항목으로 A/B 블라인드 평가를 수행한다. 실제 API는 사용자 승인 후 기존 누적 예산 내에서만 실행한다. 접근 가능한 현대 로맨스 공개 본문을 추가 조사하고, 품질 비교 근거가 쌓인 뒤 SCENE의 기존 표현 지침과 NARRATOR의 중복을 별도 변경으로 정리한다.
