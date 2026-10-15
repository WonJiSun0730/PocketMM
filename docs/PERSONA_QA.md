# 장기기억 대리 사용자 QA 실행기

## 목적과 판정 범위

실제 PocketMM 대화·기억 파이프라인을 합성 데이터로 호출하는 개발 도구다. 모델을 새로 학습시키는 작업은 아니다. 판매 가격과 목표 마진은 미정이므로 대화 원가와 기억 품질만 기록한다. 무료 모의 결과를 유료 AI 품질이나 수익성의 근거로 사용하지 않는다.

실제 테스트 실행기는 `src/test/java/com/example/projectmm/qa`에 있으며 배포 JAR에 포함되지 않는다. 추가한 웹 연결부는 명시적으로 켠 개발·QA 서버에서만 작동한다. `prod`/`production` 프로필에서는 설정을 켜도 연결부와 실행 API를 등록하지 않는다.

## 포켓 개발자 도구에서 실행

개발자 권한으로 포켓을 열고 **개발자 도구 → 자동 기억 테스트**를 펼친다. 대상 모델(Luna/Terra/Sol)과 1~1,000턴을 지정하고 시작한다. 봇은 **GPT-5.6 Luna 고정**이며, 유료 CLI 설정에서도 다른 봇 모델을 거부한다.

무료 모의 점검이 기본값이다. 실제 AI 모드에는 최대 USD 비용과 비용 발생 동의가 필요하다. 실행 중 진행률·확인된 비용·최근 10턴과 기억 근거를 볼 수 있고, 일시정지·재개·중단 및 전체 JSON/CSV/Markdown 다운로드를 제공한다. 테스트는 현재 포켓에 대응하는 별도 합성 QA 세션에서 진행한다. 현재 포켓의 내용·캐릭터·개인 기억을 테스트 DB로 복사하지 않는다.

서버의 `app.qa-ui.enabled=true`를 명시하고 다음 항목을 설정한다.

```properties
app.qa-ui.workspace=C:/path/to/PocketMM-source
app.qa-ui.maven=C:/path/to/apache-maven/bin/mvn.cmd
app.qa-ui.maven-cache=C:/path/to/prepared/maven-cache
app.qa-ui.prices-file=C:/path/to/PocketMM-source/tools/qa-ui-prices.example.json
```

Maven 설치 디렉터리 자체도 `app.qa-ui.maven`에 지정할 수 있다. UI 연결부는 Maven의 Java 런처를 별도 프로세스로 실행하므로 PowerShell 스크립트 실행 정책 변경이 필요하지 않다. 실행기는 소스 사본과 Java 25·준비된 Maven 의존성 캐시를 필요로 한다. 배포 JAR만으로 자동 테스트를 실행하지 않는다.

API 키는 서버 설정 `app.qa-ui.api-key` 또는 기존 `openai.api-key`에서 읽어 자식 프로세스의 전용 환경 변수로만 전달한다. 브라우저·설정 JSON·명령행 인수에는 넣지 않는다. 키나 가격표가 없으면 UI의 유료 모드를 비활성화한다. 예제 가격표는 2026-10-15 확인값이며 실제 실행 전에 재확인한다.

UI가 숨겨진 것과 별개로, 모든 `/api/worlds/pockets/{sessionId}/qa/**` 요청에서 `ROLE_DEVELOPER`와 현재 포켓 소유권을 검사한다. 실행 기록·보고서에도 시작한 개발자와 포켓 범위를 확인한다. 같은 요청 ID를 재전송해도 실행기를 두 번 시작하지 않는다. 서버당 UI 테스트 하나를 실행하며, 프로세스 잠금으로 같은 작업 사본의 중복 UI 작업도 막는다.

## 실제 코드 조사와 재사용

기준 커밋은 `main`의 `2d5eada016d769c88b2dd44fd5e32ceb7d41665f`다. Windows Git 인증 실행이 실패해 연결된 GitHub 도구로 커밋과 트리·파일을 읽었고, 모든 Git 객체 해시를 검증하여 별도 얕은 사본과 `feat/persona-memory-qa` 브랜치를 만들었다. 원래 작업 사본은 유지했다.

| 영역 | 확인한 기존 구현 | QA에서 사용하는 방법 |
|---|---|---|
| 서버/화면 | Java 25, Spring Boot 4.1.1, JPA / React·Vite | 서버 테스트 클래스에서 실행. 새 공개 웹 화면 없음 |
| 대화 진입 | `WorldController`의 POST `/api/worlds/{worldId}/rooms/{roomId}/sessions/{sessionId}/chat` → `WorldRoomDialogueService.chat` | 같은 서비스의 Spring 트랜잭션 프록시 호출. HTTP 인증·브라우저 동작 검증은 별도 |
| 저장 | USER/CHARACTER 메시지와 `WorldRoomMessageVariant` | 실제 저장·장면·시간·등장인물 처리 유지 |
| 기억 추출 | `PocketUserSpeechCommitted` → AFTER_COMMIT `PocketMemoryCommitListener` → `PocketMemoryService` | 실제 커밋 이벤트 사용. `SpokenText`와 발화 당시 청중 스냅샷 유지 |
| 기억 생성 | `PocketMemoryWriter`, `MemoryDetailService` | 인용 근거·중요도·중복 fingerprint 검증을 그대로 통과 |
| Narrative Anchor | `Memory.narrativeAnchor`, 이벤트에서의 생성, `MemoryDecayPolicy`의 강도 하한/압축 압력 | 새 기억 시스템 없음. 기존 정책 그대로 사용 |
| 검색/회상 | `WorldRoomContextBuilder`, `MemoryRecallService`, `MemoryRetriever`, 최근 6턴, 선택적 조회·중복 제거 | 실제 컨텍스트 빌더·감쇠·티어·접근 정책·검색·접근 시각 갱신 사용 |
| 압축 | `MemoryCompressionService`, 증거·보호 상세 검증과 독립 저장 트랜잭션 | 서비스는 실제 구현. 외부 압축기는 안전하게 null 반환. AI 압축 성능 검증은 이번 범위에서 제외 |
| AI 게이트웨이 | `OpenAiDialogueGateway`, 공유 Gemini 프롬프트 빌더, `OpenAiChatMemoryExtractor` | 유료 모드의 대상 대화·추출은 기존 어댑터. 대리 사용자만 작은 별도 요청 |
| 사용량 | `AiUsageTracker`와 버전별 진단 JSON | 역할·모델·캐시 읽기/쓰기·출력·호출 실패를 QA 장부에 저장. 기존 하드코딩 가격표 대신 QA 설정 가격표 사용 |
| 내보내기 | `frontend/src/lib/pocketTestExport.js`, 버전별 `evaluationContextJson`, `testDiagnosticsJson`, `testMemoryBefore/AfterJson` | 같은 저장된 진단·기억 스냅샷 재사용. QA 원본에는 별도 실행 상태·정답표·비용 장부 추가 |
| 기존 테스트 모드 | `app.private-test.enabled`는 로그인/비공개 배포 설정 | 데이터 격리 수단으로 사용하지 않음. QA는 별도 H2 DB 강제 |

대리 사용자의 입력 DTO에는 성격·말투·관계·목표·최근 최대 6왕복만 존재한다. 시나리오 이벤트, 정답표, 대상의 장기기억, 전체 대화는 전달하지 않는다. 사건 발화는 제어기가 직접 삽입한다. 이야기 속에서 알려줘야 할 사실과 비공개 정답표는 구분한다.

조사 중 확인한 범위 한계: 현재 `Memory.fromWorldRoomUserSpeech`는 Narrative Anchor가 0인 생성자를 사용한다. QA를 만들면서 이 제품 정책을 임의로 바꾸지 않았다. 기본 100턴 시나리오의 작중 시간도 짧아 장기간 감쇠·AI 압축 성능을 입증하지 않는다. 이를 별도로 측정하려면 시간 경과가 있는 시나리오와 해당 제품 정책을 확인해야 한다.

## 무료 실행

Java 25와 프로젝트 의존성이 있는 Maven이 필요하다. 저장소 루트에서 PowerShell로 실행한다.

```powershell
./tools/persona-qa.ps1 -Action init -RunId smoke01 -Preset Smoke
./tools/persona-qa.ps1 -Action run -RunId smoke01
./tools/persona-qa.ps1 -Action status -RunId smoke01
```

`-Maven`으로 Maven 실행 파일, `-MavenCache`로 의존성 캐시 경로를 지정할 수 있다. 오프라인 실행을 기본으로 하므로 의존성은 미리 받아 두어야 한다. Maven wrapper를 사용하려면 `-Maven ./mvnw.cmd`를 지정한다.

프리셋은 Smoke 20 / Short 100 / Medium 500 / Long 1,000왕복이다. `-Preset Custom -Turns 1002`처럼 지정할 수 있다. 첫 사건이 2턴이므로 사건 이후 정확히 1,000턴 간격 회상은 1,002턴 이상 설정해야 한다.

새 실행의 `run` 명령에 `-Scenario ./scenario.json`을 주면 사용자 정의 이벤트 배열을 읽는다. 각 항목은 `turn`, `id`, `message`, `metric`, `required`, `forbidden`, `semanticReviewRequired`를 가진다. 예를 들어 `[{"turn":2,"id":"gift","message":"내가 준 나침반의 각인은 B731이야.","metric":"STORAGE","required":["B731"],"forbidden":[],"semanticReviewRequired":false}]` 형식이다. 이 배열은 체크포인트에 보존되고 페르소나에는 전달되지 않는다.

결과와 해당 실행만의 H2 파일은 `target/pocket-qa/<RunId>/`에 저장된다. DB URL은 시작 전에 이 경로로 강제되며 운영 DB 접속 정보를 받지 않는다. API 키는 결과에 저장하지 않는다. H2 파일과 실행 ID, 설정을 같이 보존해야 프로세스 재시작 후 이어진다.

## 제어·복구

```powershell
./tools/persona-qa.ps1 -Action pause -RunId smoke01
./tools/persona-qa.ps1 -Action resume -RunId smoke01
./tools/persona-qa.ps1 -Action run -RunId smoke01
./tools/persona-qa.ps1 -Action stop -RunId smoke01
```

일시정지와 중단은 진행 중 요청이 끝난 다음 턴 경계에서 반영한다. 중단은 종료 상태다. `run -Steps 3`으로 한 프로세스에서 진행할 최대 턴 수도 제한할 수 있다. 시작·재개는 기존 설정과 체크포인트가 같아야 한다.

요청 ID는 실행 ID와 턴 번호로 고정된다. 대화와 QA 완료 영수증은 같은 DB 트랜잭션에 저장한다. 영수증이 있으면 대화를 다시 보내지 않는다. 같은 요청 ID에 다른 입력을 재사용하면 거부한다. 파일 기록에는 OS 잠금과 원자적 교체를 사용한다. 스크립트는 실행별 프로세스 잠금도 유지한다.

요청 도중 프로세스가 죽으면 완료 영수증을 먼저 확인한다. 커밋된 응답은 다음처럼 재전송 없이 복구할 수 있다.

```powershell
./tools/persona-qa.ps1 -Action run -RunId smoke01 -RecoverCommitted
```

응답은 저장됐지만 추출/사용량 확정 전에 중단됐다면 비용 미확인 상태로 멈춘다. 사라진 기억 추출을 임의로 다시 실행하지 않는다. 서비스의 실제 실패·누락으로 보고한다.

커밋이 없는 실패 요청을 재시도하려면 공급자 사용 기록을 사람이 확인하고, 다음 JSON에 미확인 `callId`별 추가 비용과 확인 근거를 기록한다. 모의 실패에 0원을 쓰는 경우에도 근거를 적어야 한다. 확인되지 않은 유료 실패를 0원으로 가정하지 않는다.

```json
{"costs":{"미확인-callId":"0.00123"},"evidence":"공급자 사용 기록에서 해당 요청의 청구액 확인"}
```

```powershell
./tools/persona-qa.ps1 -Action run -RunId smoke01 -Reconciliation ./reconciliation.json
```

기존 커밋이 발견되면 재시도를 거부하고 복구를 요구한다. 유료 실행의 복구·재시도에도 해당 실행 승인이 필요하다. 재시도 비용은 기존 장부에 남으며 예산을 다시 검사한다.

## 유료 모드 설정

이번 개발에서는 유료 API를 호출하지 않았다. 모의 설정을 유료로 바꿔 실행하려면 사람의 실행 승인이 별도로 필요하다. 기본 스크립트는 항상 무료 모의 설정을 만든다.

`config.json`의 `paid=true`, `approval=<RunId>`, 독립된 `personaModel`/`targetModel`/`extractionModel`, 양수인 `budgetUsd`/`turnReservationUsd`, 모델별 `prices`를 설정한다. 대리 사용자 모델은 사용자 결정에 따라 gpt-5.6-luna로 고정한다. 대상·추출 모델과 가격은 운영자가 검증한 값을 넣으며, 유료 가격은 외부 설정으로 관리한다.

가격은 USD / 100만 토큰 단위다. 각 모델에 `input`, `cached`, `written`, `output`, `largeContextThreshold`(미사용 0), `largeInputMultiplier`, `largeOutputMultiplier`, `source`, `checkedAt`를 지정한다. 캐시 쓰기 가격을 입력 가격과 혼동하지 않는다. 긴 컨텍스트 할증이 있으면 함께 설정한다. 세금·환율·별도 서비스 요금은 포함하지 않는다.

전용 `POCKET_QA_OPENAI_API_KEY` 환경 변수와 다음 명시적 옵션이 모두 있어야 시작한다.

```powershell
./tools/persona-qa.ps1 -Action run -RunId paid01 -ApprovePaidRun paid01
```

실행 전 `턴 수 × 턴 예약액` 참고값을 표시한다. 캐시 적중률을 확정값으로 가정하지 않는다. 매 턴 예약뿐 아니라 모든 HTTP 호출 직전에 UTF-8 요청 크기와 출력 상한으로 보수적 비용을 다시 검사한다. 가격표·토큰 상한·서비스 가격 변경 때문에 공급자 청구 한도를 대체하는 절대 보장은 아니다. 공급자 측 예산 한도와 병행할 수 있다.

대상 출력 상한은 기본 2,048토큰, 대리 사용자 상한은 512토큰이다. 대상의 짧은 출력이 품질을 떨어뜨리면 실험 조건으로 기록하고 변경해야 한다. JVM 속성 `pocket.qa.target-max-output`, `pocket.qa.persona-max-output`, `pocket.qa.persona-reasoning`으로 조정 가능하다. 출력 상한에는 보이지 않는 생성 토큰도 포함될 수 있다. 공식 [Responses 텍스트 생성 문서](https://developers.openai.com/api/docs/guides/text), [토큰 계측 문서](https://developers.openai.com/api/docs/guides/token-counting)를 참고했다.

사용량 없는 실패, 캐시 쓰기 사용량 누락, 해석 불가 응답은 비용 미확인으로 남기고 다음 호출을 막는다. 출력 JSON 해석 실패라도 이미 받은 사용량은 보존한다. 과금 API 자동 재시도는 하지 않는다. 모델 접근권·실제 응답 품질·캐시 적중은 유료 검증 전까지 확인되지 않은 상태다.

현재 유료 어댑터는 OpenAI 대상·대리 사용자·기억 추출 경로다. Gemini 유료 QA 어댑터와 AI 압축/임베딩 모델 비교는 미구현이다. 일반 코드의 실제 감쇠/검색 경로와 외부 AI 압축 성능을 혼동하지 않는다.

## 평가·내보내기

`raw.json` 원본, `turns.csv` 턴별 지연·사용량·원가, `calls.csv` 역할·모델·요청별 토큰·원가, `report.md` 요약과 근거를 만든다. 100턴 이하에서는 매 턴, 그보다 긴 실행에서는 10턴마다 리포트를 갱신하고 종료 시 다시 쓴다. 체크포인트는 모든 단계에서 저장한다.

기억 저장은 주입한 발화의 근거 인용이 실제 기억에 존재하는지 검사한다. 회상·정정·충돌·환각·관계는 단어 포함 여부만으로 의미적 정답을 확정하지 않고 `UNEVALUATED`로 남긴다. 사람이 읽을 응답·조회 근거·규칙 후보 결과를 함께 보존한다. 통과율의 분모는 실제 평가된 항목만이다. AI 판정기는 미사용이며 비용도 발생하지 않는다.

실행 종료가 `COMPLETED`여도 품질 검사가 모두 통과했다는 뜻은 아니다. 가격 미정 상태에서 자동으로 “수익화 가능”을 선언하지 않는다. 사용자당 대화량·유료 전환·판매 가격·목표 마진은 별도 의사결정 항목이다.

선택적 A/B는 동일 설정의 별도 RunId 두 개를 만들고 한쪽 `memoryEnabled=false`로 실행한다. QA의 추출 어댑터와 조회 어댑터에서만 끄며 운영 서비스를 변경하지 않는다. 최근 대화는 양쪽 모두 유지하므로 장기기억만의 차이와 최근 대화 효과를 구분해야 한다. 대리 사용자 응답이 달라질 수 있어 단일 쌍만으로 인과효과를 확정하지 않는다. 전용 A/B 비교 화면·통계 검정은 미구현이다.

## 정리

기본적으로 결과를 보존한다. 필요하면 다음 명령으로 **그 실행의 합성 DB·체크포인트·리포트만** 삭제한다. 실행 중에는 프로세스 잠금 때문에 거부된다.

```powershell
./tools/persona-qa.ps1 -Action delete -RunId smoke01
```

## 자동 검증

```powershell
mvn -Dtest=QaRunnerTest,QaPipelineIntegrationTest,QaPersonaClientTest,QaSpendGuardTest test
```

모의 20/100턴 실제 저장·검색 통합, 500/1,000턴 실행기, 정답표 비노출, 일시정지·재개, 중복 방지, 세션 격리, 예산 차단, 사용량 누락, 실패 비용 보존, 명시적 재시도와 리포트 근거를 검증한다. `PersonaQaExecutionTest`는 `pocket.qa.execute=true`가 없는 일반 테스트에서는 비활성이다. 기존 `live` 태그 테스트는 실행하지 않는다.
