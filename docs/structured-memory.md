# Structured Memory

## 실행 경로

ChatService가 채팅을 확정한 뒤 ChatMemoryService가 사용자 발언만 추출한다.
GeminiChatMemoryExtractor는 기존 한 번의 추출 요청에서 인용·중요도와 선택적 fact를 반환한다.
ChatMemoryWriter는 원문 발언과 후보를 검증하고 방 잠금 아래 기존 fingerprint로 중복을 방지한다.
MemoryRecallService → DialogueContextBuilder → GeminiDialogueGateway가 구조와 원문 근거를 함께 전달한다.

## 저장 계약

- quote: 의미를 보존한 사용자 발언의 원문 부분 문자열, 2–400자.
- importance: 기존 4–10 범위 유지.
- fact: kind / subject / relation / value. 안전하게 분해할 수 없으면 null.
- kind: IDENTITY, PREFERENCE, RELATIONSHIP, CONSTRAINT, PLAN, REPORTED_EVENT.
- subject/relation/value: 반드시 같은 quote에 있는 표현. 번역·추측·누락 주체 보충은 허용하지 않는다.
- 구조는 사용자 발언을 검색하기 위한 정보다. 객관적 사실의 확인이나 계획의 실행을 뜻하지 않는다.
- 부정·불확실성·전언·계획 여부는 전체 evidenceQuote를 우선하여 해석한다.

잘못된 구조는 후보 단위로 폐기한다. 기존 quote/importance만 있는 응답은 호환한다.
다만 모든 후보에 문장 경계를 검사하여 부정/인용 맥락을 잘라낸 조각과 물음표가 있는 질문은 거절한다.
이는 의미 판별기가 아니며, 경계가 모호한 정상 발언도 보수적으로 누락될 수 있다.
추출 장애는 저장된 채팅을 실패시키지 않는 기존 정책을 유지한다.

## DB 호환성

기존 memories 테이블에 nullable evidence_quote(400), fact_kind(32), fact_subject(120),
fact_relation(120), fact_value(400)만 추가한다. 별도 테이블이나 백필은 없다.
현재 ddl-auto=update 환경에서는 다음 애플리케이션 시작 때 컬럼이 추가된다.
스키마를 별도 관리하는 배포 환경에서는 같은 nullable 컬럼을 먼저 추가해야 한다.
기존 행과 세계 사건 기억은 null 그대로 기존 summary 회상 경로를 사용한다.
원문 근거는 summary 압축과 별도로 보존한다.

## 회상과 범위

기존 방/캐릭터 필터, 독립 시작 정책, 미래 기억 제외, 감쇠, 회상 개수 제한을 유지한다.
구조 유무와 무관하게 요약과 원문 중 더 높은 검색어 일치를 사용한다.
대상·관계·값은 이미 원문에 포함되므로 중복 가산하지 않는다. 동점은 기존 강도·기억 시각 순서를 따른다.
DialogueContextBuilder는 다른 캐릭터 소유 방을 거부한다.
프롬프트에는 사용자 진술이라는 출처, 기억 시각, 구조 및 인용 근거를 함께 넣는다.
외부 REST 응답 및 프런트 메모리 디버그 표시 형식은 바꾸지 않는다.

## 한계

부분 문자열 검증은 의미적 모순이나 잘못된 분류까지 증명하지 않는다.
명시되지 않은 주체는 추론하지 않고 인용형으로 남긴다. 기존 행의 자동 구조화는 없다.
같은 인용 중복만 방지하며 의미 중복 제거, 정정에 의한 과거 기억 대체, 프로필별 재분류는 미구현이다.
검색은 여전히 문자열 기반이다. 벡터 검색·새 lifecycle·기억 3계층을 도입하지 않는다.
