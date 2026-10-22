# QA 1843: 첫 연속성 검사 거절

실행 exp-76615c75441a3aca88cf1843의 로컬 기록을 읽었으며 유료 재실행은 하지 않았다.

- 5개 API 호출은 모두 completed다. Sol 생성 뒤 Luna REVIEW에서 거절되어 완료 턴은 0이다.
- 실패 위치는 SceneContinuityService.generate의 첫 requireReview이며 VERIFY나 사실 apply 단계가 아니다.
- 예외는 Scene continuity review did not approve the response다. approved=false, conflicts 비어 있지 않음, changes/conflicts 누락 중 어느 조건인지는 기존 기록에 없다.
- 소설 생성 $0.0497370, 연속성 검사 $0.00274945, 초기 설정 $0.00219045, 시간 $0.00039090, 상대 $0.00096975. 합계 $0.05603755.

이전 진단 수정은 예외 위치만 보존하고 검사 결정/충돌 내용은 보존하지 못했다. 이번 수정은 ReviewRejectedException에 Review 전체를 담고 QA failedOutcome.diagnosticsJson의 continuityReview로 기록한다. 다음 기록에는 approved, conflicts, changes 및 수정본을 확인할 수 있다. 검사 API 응답의 output_text도 review-responses/<callId>.json으로 보존하고 소유자 인증을 거치는 프롬프트 ZIP에 포함한다. 인증 헤더와 임의의 공급자 예외 문구는 포함하지 않는다. 응답 저장 실패가 이미 발생한 비용 장부 기록을 막지 않는다.

검사 거절을 자동 승인하거나, 추가 유료 재시도/상위 모델 호출을 하지 않는다. 기존 실행에서 소실된 검사 응답은 복구할 수 없고 아직 어떤 모순 때문에 거절됐는지는 미확정이다. 이번 변경이 원래 거절 사유 자체를 해결했다는 뜻은 아니다. 추가 진단이 필요하더라도 기존 Sol 후보를 재사용하는 검사 전용 재현이 가능하므로 새 소설 생성부터 반복할 필요는 없다. 유료 실행은 사전 승인 후에만 수행한다.

무료 로컬 테스트 23개 통과: 거절 결정/충돌 보존, 응답 파일과 비용 장부 병행 보존, 인증 헤더 제외, 기존 연속성 차단 및 예산/QA UI 회귀. 실패 승인이나 유료 호출은 없다.
