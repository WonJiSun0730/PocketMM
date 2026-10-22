# 연속성 검사 비용 최적화 (2026-10-22)

## 후속 사용자 결정: Luna로 비용 제한

사용자 요청에 따라 신규 QA 대상 모델을 Luna로 제한했다. 일반 QA/체험 QA 모두 capabilities에서 Luna만 제공하고 서버도 Sol/Terra 실행 및 재개를 거부한다. 플레이어 대역은 기존 Luna를 유지한다. 연속성 검사 기본값과 직접 생성되는 QA 게이트웨이 기본값도 Luna다. 따라서 신규 UI QA는 소설 생성·플레이어·검사 모두 Luna를 사용한다. 기존 실행의 기록/설정은 재작성하지 않는다. 이미 진행 중인 Sol 실행은 중지하고 Luna로 새 실행을 만들어야 한다. 유료 API 실행은 하지 않았다.

후속 무료 검증: SceneContinuityRequestTest, QaUiServiceTest, ExperienceUiServiceTest, ExperienceBudgetTest 총 28개 통과(실패/오류/건너뜀 0). 신규 고비용 모델 요청이 worker 실행 전에 거부되고 생성된 QA 설정의 대상/플레이어가 Luna이며 기본 검사 요청도 Luna인지 확인했다. 기존 캐시 경계 및 승인 검사는 유지한다.

아래 기본 검사 모델 유지 및 후보 미전환 설명은 최초 구현 당시 기록이다. 현재 기본값은 위 사용자 결정이 우선한다. 실서비스 Pocket의 소설 모델 선택은 별도 기존 설정이며, QA의 모델 제한은 신규 QA 복사본에 적용된다. 명시적인 openai.continuity-model 설정은 여전히 기본값을 덮어쓸 수 있다.

## 기준 상태와 보존

원본 `C:\SpringWork\projectmm`은 main, HEAD `a8dd3bc5a72d60952ea6c3506176baa9a4b5af49`였다. 추적 파일 변경은 없었다. 미추적 `.mvn/jvm.config`, data/, log/, jansi DLL, 개인 QA 모델 한도 및 가격 백업 파일은 수정하거나 삭제하지 않았다. 원본 쓰기 권한이 부여되지 않아 이 작업 공간의 work/PocketMM에 추적 파일과 Git 이력을 복사하고 `perf/continuity-cost-optimization` 브랜치를 만들었다. 개인 설정, API 키, DB, 로그를 개발 복사본에 복사하지 않았다.

직접 fetch는 Windows 인증 오류로 실패했다. 연결된 GitHub 도구에서 main의 최신 SHA가 위 HEAD와 같음을 확인했다. develop 검색 결과는 없었다. 원본 로컬 원격 참조에도 develop은 없다. 원본 checkout, main, 원격 브랜치는 변경하지 않았다.

## 실제 기록 검증

원본 실행: `target/pocket-qa-experience/exp-793c11a02341695ea10973e8`. calls.csv, raw.json, turns.csv, ledger.jsonl, requests/*.json, analysis.json, worker.log를 읽었다. 재실행하지 않았다.

| 목적 | 호출 | 기록된 USD | 출력 토큰 |
|---|---:|---:|---:|
| 소설 생성 ROLEPLAY | 5 | 0.1529460 | 3,367 |
| 연속성 검사 SCENE_CONTINUITY | 5 | 0.2948762 | 4,798 |
| 초기 장면 | 1 | 0.0437570 | 111 |
| 시간 해석 | 4 | 0.00160835 | 316 |
| 기억 추출 | 3 | 0.00342935 | 2,124 |
| 기억 임베딩 | 12 | 0.00002906 | 0 |
| 테스트 플레이어 | 5 | 0.00719550 | 1,130 |
| 합계 | 35 | 0.50384146 | |

검사 입력 44,228, 캐시 읽기 4,828(10.916%), 쓰기 39,385토큰이다. 당시 Sol 쓰기 단가 $5/백만으로 쓰기 비용은 $0.196925다. 소설 입력 56,434 중 캐시 읽기 38,925(68.98%). 검사 개별 지연은 6.850673, 8.355644, 12.404807, 13.470957, 6.840693초이다.

완료 4턴 합계 $0.38214034에는 테스트 플레이어가 포함된다. 플레이어를 제외한 완료 서비스 비용은 $0.37678684, 턴 평균 $0.09419671이다. 미완료 5턴 비용은 $0.12170112다. raw 상태는 REVIEW_REQUIRED/TARGET_IN_FLIGHT이며 자동 재시도 금지다. 호출표의 비용은 QA 장부 계산값이며 공급자 청구서와 대조한 값은 아니다.

검사 5개 요청 모두 REVIEW이며 BOOTSTRAP/VERIFY 반복은 없었다. 요청 모델은 모두 gpt-5.6-sol이다. 기존 evidence 필드 순서는 mode → facts → userInput → candidate → history → reference이다. facts는 0, 8, 14, 23, 34개로 증가했다. 고정 참고자료가 변화하는 facts와 candidate 뒤에 있어 긴 공통 접두사를 재사용할 수 없었다. 기존 검사 요청에는 explicit 캐시 옵션도 없었다. 2회차부터 캐시 읽기는 매번 1,207토큰이었다.

현재 코드와 기록된 요청에는 이미 `changes` 스키마와 무수정 시 turn=null 규칙이 있다. 따라서 '전체 사실 재출력'을 확정 원인으로 단정하지 않는다. 저장된 검사 응답 원문이 없는 상태에서 4,798 출력 토큰만으로 반복 사실·인용·신규 사실의 기여도를 분해할 수 없다. 사실 증가 및 출력량을 줄이되 필요한 사실 누락을 허용하지 않는 방향으로 지침을 명확히 했다.

## 구현

1. OpenAiDialogueGateway에 `openai.continuity-model` 선택 설정을 추가했다. 빈 값은 기존 Pocket 모델을 따른다. Spring 설정 또는 환경 변수 OPENAI_CONTINUITY_MODEL로 검사만 독립 선택할 수 있다. REVIEW/VERIFY/BOOTSTRAP의 동일 검사 경로에 적용되며 사용량은 실제 선택 모델로 기록된다. 소설 생성, 초기 장면, 테스트 플레이어, 기억 추출 모델은 변경하지 않는다. 의미 품질 비교 전에는 기본 모델을 바꾸지 않는다. Gemini의 공급자 및 생성 모델은 유지한다.
2. SceneContinuityRequest가 authored 월드·프로필·소개·노트·가이드·캐릭터 정의·시스템 정의를 stableReference로 분리한다. 캐릭터/시스템 정의는 ID 정렬한다. 시간, 에너지·기분·출입 상태, 시스템 현재 수치, 플레이어 상태, 검색 기억, 최근 대화, 사실, 후보는 dynamic 자료에 남긴다. 필드를 삭제하거나 기억을 요약하지 않는다. 설정을 편집하면 접두사도 바뀌어 자연스럽게 캐시가 무효화된다.
3. OpenAI 요청은 developer 검사 지침 → user 고정 JSON(명시적 캐시 경계) → user 동적 JSON 순이다. authored 자료를 developer 지침으로 승격하지 않는다. explicit-only 모드로 변화하는 본문/사실을 캐시에 계속 쓰지 않도록 한다. Gemini는 같은 자료를 고정 JSON 먼저 직렬화하지만 OpenAI 전용 옵션을 보내지 않는다. 공급자 캐시 적중·지연 개선은 아직 실측하지 않았다.
4. 기존 facts는 changes에 없어도 유지됨, 변화가 없으면 changes=[], 같은 값·확실성 재언급 금지, 충분히 짧은 원문 인용, 지속되는 사실만 추출하도록 지침을 보강했다. SceneFacts는 기존 검증을 먼저 통과한 무변화 변경에 대해 원래 근거를 유지한다. RETIRE, 확실성 변경, 사용자 설정 변경은 계속 처리한다. 출력 상한을 무리하게 낮추지 않았다.

SceneContinuityService의 승인 게이트, 수정 후 독립 VERIFY, 인용·이전 값·중복 키·certainty 검증, 96개 한도, 버전별 체크포인트, 실패 시 롤백, 확정 후 기억 처리는 유지했다. 최근 원문 창 밖의 사실과 재생성/편집 시 이력 재구성도 유지했다.

## 후보 비교와 절감 추정

후보는 현재 통합과 같은 OpenAI Responses/strict structured output을 지원하는 Luna, Terra로 제한한다. [Luna 문서](https://developers.openai.com/api/docs/models/gpt-5.6-luna), [Terra 문서](https://developers.openai.com/api/docs/models/gpt-5.6-terra), [캐시 문서](https://developers.openai.com/api/docs/guides/prompt-caching)를 2026-10-22 확인했다. 저가라는 이유로 의미 정확도가 같다고 가정하지 않는다.

| 모델 | 입력/캐시 읽기/쓰기/출력 USD(백만 토큰) | 같은 기록 토큰의 검사 5회 USD | 검사 절감 | 전체 35회 가정 합계 |
|---|---|---:|---:|---:|
| Sol 기준 | 4 / .4 / 5 / 20 | .29487620 | — | .50384146 |
| Terra 후보 | 2 / .2 / 2.5 / 12 | .15703410 | 46.75% | .36599936 |
| Luna 후보 | .2 / .02 / .25 / 1.2 | .01570341 | 94.67% | .22466867 |

계산식은 `(input-cached-written)*inputRate + cached*cachedRate + written*writeRate + output*outputRate`, 이후 백만으로 나눈다. tools/qa-ui-prices.example.json의 당시 저장 단가와 동일한 기록 토큰으로만 재계산했다. 모델이 달라지면 출력량·수정율·검증 횟수도 달라질 수 있다. 캐시 재배치나 출력 최소화 절감은 이 표에 더하지 않았다. 기본 모델은 유지했으므로 위 수치는 이번 코드 변경의 실현 절감률이 아니다.

## 품질 비교 절차 (유료 실행 전 승인 필요)

실제 모델별 의미 품질은 아직 미측정이다. 무료 테스트의 고정 응답은 모델 평가로 간주하지 않는다. 승인받은 별도 예산으로 같은 증거 세트를 Sol/Terra/Luna에 전달하고 사람이 정답과 대조해야 한다. 번호와 순번, 다른 기관의 번호, 소지자·전달·최종 수신, 열람 완료, CLAIM/OBSERVED, 사용자 retcon과 OOC 질문, 메타데이터, 근거 위조, 이전 값 불일치, 6턴 밖 미해결 사실, 수정 후 VERIFY, BOOTSTRAP 시간 순서, 편집/삭제/대안 선택을 포함한다.

치명적 모순의 거짓 승인·잘못된 사실 저장은 0건이어야 하며 사용자 의도 침해/불필요 거부도 비교한다. 합격 후보만 별도 50~100턴 장기 테스트로 기억의 출처·회상·미해결 단서 보존을 확인한다. p50/p95 지연, 호출수, 거부/수정 비율, 입력·읽기·쓰기·출력과 실제 비용을 함께 기록한다. 런타임 모델 설정과 독립 QA 프로세스가 같은 설정을 사용하도록 확인해야 한다. 기존 QA 실행은 재개하지 않고 새 manifest/실행 ID로 분리한다. 자동 상위 모델 재호출을 추가하지 않았다.

## 검증과 남은 위험

무료 로컬 검증은 총 71개, 실패 0, 오류 0, 건너뜀 0으로 BUILD SUCCESS다. 검사 요청은 never-contact.invalid용 인메모리 HTTP 인터셉터로만 처리한다. 통합 테스트는 임베디드 H2와 모의 게이트웨이를 사용한다. Maven offline 모드로 실행했다. 유료 API 호출, 배포, 원격 push는 하지 않았다.

실행 테스트: SceneContinuityRequestTest, SceneFactsTest, SceneContinuityServiceTest, PocketHistoryWindowTest, WorldRoomDialogueWorldlineIntegrationTest, PromptMigrationEquivalenceTest, WorldRoomCharacterContinuityPromptTest, MemoryConversationScenarioIntegrationTest, MemoryPersistenceIntegrationTest, MemoryRetrievalEvaluationTest, MemoryAccessRecallBoundaryTest, PocketMemoryAudienceSnapshotTest, MemoryCompressionProvenanceTest, OpenAiPocketMemoryPersistenceTest, ExperienceBudgetTest. 검사 모델 분리/실제 모델 사용량 기록, 고정 경계의 상태·모드 독립성, 설정 편집 무효화, 인용 검증 후 무변화 사실의 원래 근거 보존과 기존 연속성·버전·기억 회귀를 확인했다. 초기 테스트에서 로컬 토큰 추정기가 배열 content를 문자열로 읽는 오류를 발견해, 전송 요청은 유지하면서 추정기에 텍스트만 전달하도록 수정한 뒤 위 71개를 다시 통과했다. `git diff --check`와 배포 패치의 역방향 적용 검사도 통과했다.

변경 파일(9개):

- src/main/java/com/example/projectmm/infrastructure/dialogue/OpenAiDialogueGateway.java
- src/main/java/com/example/projectmm/infrastructure/dialogue/SceneContinuityRequest.java
- src/main/java/com/example/projectmm/application/dialogue/SceneContinuityPrompt.java
- src/main/java/com/example/projectmm/domain/dialogue/SceneFacts.java
- src/test/java/com/example/projectmm/infrastructure/dialogue/SceneContinuityRequestTest.java
- src/test/java/com/example/projectmm/domain/dialogue/SceneFactsTest.java
- tools/audit-continuity-cost.py
- docs/continuity-cost-optimization.md
- docs/scene-continuity.md

남은 위험: 실제 모델 의미 품질과 캐시 적중/지연 미검증, 최초 캐시 쓰기·만료·설정 편집 시 재쓰기, 필수 사실이 많아질 때 출력 증가와 96개 한도 실패, 자연어 증거의 의미 일치가 여전히 모델에 의존한다. 표현이 다른 의미상 같은 사실까지 코드가 자동 병합하지 않는다. 기존 실패 게이트를 완화하거나 unresolved 사실을 잘라 비용을 줄이지 않는다.

오프라인 재집계:

```powershell
python tools/audit-continuity-cost.py <기존 QA 실행 폴더> --output <결과 JSON 경로>
```

이 스크립트는 네트워크 및 모델 호출 없이 CSV/JSONL/기록된 요청만 읽는다. 원문 대화·개인 데이터·키는 보고서에 내보내지 않는다.
