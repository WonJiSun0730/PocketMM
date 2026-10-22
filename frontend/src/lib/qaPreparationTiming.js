export const qaPreparationStages = [
  ["SESSION_LOCK","세션 잠금"],
  ["INITIAL_SCENE_SETUP","초기 장면 준비"],
  ["USER_MESSAGE_SAVE","사용자 입력 저장"],
  ["PLAYER_STATE_SNAPSHOT","플레이어 상태 조회"],
  ["SCENE_INSTRUCTION","장면 변경 지시 판단"],
  ["TIME_INTERPRETATION","시간 판단"],
  ["CONTEXT_BUILD","문맥·설정 구성"],
  ["CONTEXT_MEMORY_RECALL","기억 조회 (문맥·초기 설정 등에 포함)"],
  ["STATE_SNAPSHOT","등장인물·시스템 상태 조회"],
  ["SCENE_FACTS_PREPARE","기존 연속성 사실 준비"],
  ["TARGET_GENERATION","생성 경로 전체 (프롬프트·사전검증·HTTP·파싱 포함)"],
  ["TARGET_TOKEN_COUNT","QA 입력 토큰 계수 (생성 경로에 포함)"],
]
export function qaPreparationRows(summary) {
  return qaPreparationStages.map(([key,label])=>({key,label,stats:summary?.[key]}))
    .filter(row=>row.stats?.count > 0).sort((a,b)=>b.stats.average-a.stats.average)
}
export function qaTimingSeconds(value) {
  return Number.isFinite(value)&&value>=0 ? `${(value/1000).toFixed(3)}초` : "미측정"
}
