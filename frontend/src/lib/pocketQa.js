export const QA_PERSONA_MODEL = "gpt-5.6-luna"
export const qaStatusLabels = { STARTING: "실행 환경 준비 중", RUNNING: "진행 중", PAUSING: "일시정지 요청 중", PAUSED: "일시정지", STOPPING: "중단 요청 중", STOPPED: "중단됨", COMPLETED: "완료", INTERRUPTED: "연결 중단 · 재개 가능", REVIEW_REQUIRED: "비용·실패 확인 필요", BUDGET_STOP: "예산으로 중단", FAILED: "실행 준비 실패" }
export function qaModelLabel(model) { return ({ "gpt-5.6-luna": "GPT-5.6 Luna", "gpt-5.6-terra": "GPT-5.6 Terra", "gpt-5.6-sol": "GPT-5.6 Sol" })[model] ?? model }
export function qaEstimate(capabilities, model, turns) {
  const rate = capabilities?.perTurnEstimates?.[model]
  if (!rate || !Number.isInteger(Number(turns)) || Number(turns) < 1) return null
  return { low: Number(rate.lowUsd) * Number(turns), high: Number(rate.highUsd) * Number(turns) }
}
export function qaStartRequest({ requestId, targetModel, turns, paid, budgetUsd, acknowledgeCost }, capabilities) {
  const count = Number(turns), budget = Number(budgetUsd)
  if (!capabilities?.targetModels?.includes(targetModel) || !Number.isInteger(count) || count < 1 || count > capabilities.maxTurns)
    throw new Error(`대상 모델과 1~${capabilities?.maxTurns ?? 1000}턴을 지정해 주세요.`)
  if (paid && (!capabilities.paidAvailable || !Number.isFinite(budget) || budget <= 0 || !acknowledgeCost))
    throw new Error("유료 실행에는 최대 비용과 비용 발생 동의가 필요해요.")
  return { requestId, targetModel, turns: count, paid, budgetUsd: paid ? budget : 0, acknowledgeCost: paid && acknowledgeCost }
}
export async function qaRequest(url, options = {}) {
  const response = await fetch(url, { credentials: "include", ...options })
  const text = await response.text()
  let data
  try { data = text ? JSON.parse(text) : null } catch { data = null }
  if (!response.ok) {
    const error = new Error(data?.detail ?? data?.message ?? (response.status === 403 ? "개발자 권한이 필요해요." : response.status === 404 ? "이 서버에서는 자동 테스트가 꺼져 있어요." : "자동 테스트 요청을 처리하지 못했어요."))
    error.status = response.status
    throw error
  }
  return data
}
