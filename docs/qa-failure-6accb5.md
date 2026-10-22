# QA 실행 6accb5 조사

2026-10-22 실행 exp-b9f2e50e02645f77f86accb5의 calls.csv, ledger.jsonl, run.json, worker.log를 읽었다. 유료 재실행이나 원본 데이터 변경은 하지 않았다.

- 완료 1턴 $0.05287070, 미완료 2턴 $0.02430571, 총 9회 $0.07717641.
- 소설 생성 2회는 Sol로 $0.0700430. 연속성 검사 2회는 Luna로 $0.00219931. 초기 장면/시간/플레이어도 Luna다.
- 두 번째 검사 입력 6,050토큰 중 4,493토큰(74.26%)이 캐시 읽기이고 캐시 쓰기는 0이었다. 검사 비용은 $0.00091606이었다.
- 9개 호출 모두 completed이며 마지막 검사 뒤 추가 유료 호출은 없다. API 응답 수신 이후의 로컬 처리/검증/저장 과정에서 RuntimeException이 발생했다. 어느 로컬 검증인지는 확정할 수 없다.
- QaPipeline.send가 RuntimeException을 잡아 예외/원인을 모두 버리고 동일 문구만 Outcome.error에 넣었다. QaRunner도 실패한 Outcome을 보존하지 않았다. 그래서 현재 UI/JSON/worker.log에 정확한 예외가 남지 않는다. 예산 부족이나 Luna 오판으로 단정할 수 없다. 과거 예외와 검사 응답 본문은 복구할 수 없다.

수정: QaPipeline이 예외 원인 체인의 타입, 로컬 코드 위치, SceneFacts/SceneContinuityService에서 발생한 로컬 검증 문구만 diagnosticsJson에 담는다. 임의의 공급자 예외 메시지·인증정보·프롬프트는 저장하지 않는다. QaRunner는 실패한 Outcome을 failedOutcome으로 체크포인트에 남긴다. 전체 원본 JSON의 execution.failedOutcome.diagnosticsJson에서 다음 실패의 원인을 확인할 수 있다. 실패를 완료 턴으로 세거나 자동 재시도하지 않으며 기존 검사/원본 실행/모델 설정을 변경하지 않는다.

이 변경은 실패 원인 유실을 고친 것이며, 6accb5의 중단 원인 자체를 해결했다고 주장하지 않는다. 이미 사라진 예외는 새 코드로 소급 복원되지 않는다. 추가 유료 실행은 사용자의 사전 승인 없이 수행하지 않는다.

## 사용자 내보내기 자료 대조

Downloads/10221449의 JSON, Markdown, calls/turns/metrics CSV 및 prompts ZIP을 추가 확인했다. ZIP의 요청 9개와 calls CSV는 로컬 원본과 바이트가 동일하다. ZIP에는 응답 파일이 없고 JSON에는 failedOutcome 및 실패 예외가 없다. budget-events의 마지막 phase는 SETTLED, uncertain=false, 예약액 0, 잔액 $9.92282359다. 예산 차단은 아니다. CSV의 완료 턴은 1개뿐이다.

새로 확인된 품질 점검 사항: 2턴째 검사 요청 facts가 []이므로 첫 턴에서 활성 장면 사실을 생성하지 않았다. 첫 턴이 검사를 통과했다는 사실과 장면 사실 추출이 충분하다는 판정은 다르다. 두 번째 요청에는 첫 턴 원문이 recentMessages로 전달됐으므로 당시 문맥이 전혀 없는 것은 아니다. 이후 원문 창 밖으로 나갈 사실의 보존 품질은 별도 확인이 필요하다. 이것을 중단의 확정 원인으로 취급하지 않는다.

무료 검증: 최종 진단 보존/비밀정보 제외/자동 재시도 금지/복구 테스트 5개 통과. 별도 실제 서비스 경로의 QaPipelineIntegrationTest 2개도 통과했다. 넓은 실행기 테스트의 500/1,000턴 모의 반복은 오래 걸려 중단하고 관련 3개 메서드로 범위를 좁혔다. 전체 실행기 테스트를 모두 통과했다고 보고하지 않는다.
