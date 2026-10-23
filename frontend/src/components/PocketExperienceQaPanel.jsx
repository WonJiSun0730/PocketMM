import { useEffect, useRef, useState } from "react"
import {qaPreparationRows,qaPreparationStages,qaTimingSeconds} from "@/lib/qaPreparationTiming"
import { Button } from "@/components/ui/button"
import { qaEstimate, qaModelLabel, qaRequest, qaStartRequest, qaStatusLabels, qaCostStatus, qaUsd as usd } from "@/lib/pocketQa"

const field = "mt-1 h-9 w-full rounded-md border bg-background px-2 text-sm"

export default function PocketExperienceQaPanel({ sessionId }) {
  const base = `/api/worlds/pockets/${sessionId}/experience-qa`
  const [capabilities, setCapabilities] = useState(null)
  const [unavailable, setUnavailable] = useState("")
  const [targetModel, setTargetModel] = useState("gpt-5.6-sol")
  const [turns, setTurns] = useState(20)
  const [paid, setPaid] = useState(false)
  const [budget, setBudget] = useState("")
  const [acknowledge, setAcknowledge] = useState(false)
  const [runs, setRuns] = useState([])
  const [selectedId, setSelectedId] = useState(null)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const [refresh, setRefresh] = useState(0)
  const requestId = useRef(null)
  const [analysisData, setAnalysis] = useState(null)
  const [reviewNotes, setReviewNotes] = useState({})
  const [reviewTypes, setReviewTypes] = useState({})
  const [conversation, setConversation] = useState([])
  const [offset, setOffset] = useState(0)
  const [conversationTotal, setConversationTotal] = useState(0)
  const selected = runs.find(run => run.id === selectedId) ?? runs[0]
  const analysis = analysisData?.runId === selected?.id ? analysisData?.value : null
  useEffect(() => {
    if (!selected?.id || selected.completed === 0) return
    let active = true
    Promise.all([qaRequest(`${base}/runs/${selected.id}/analysis`), qaRequest(`${base}/runs/${selected.id}/conversation?offset=${offset}&limit=20`)])
      .then(([a, log]) => { if (active) { setAnalysis({ runId: selected.id, value: a }); setConversation(log.turns); setConversationTotal(log.total) } })
      .catch(cause => { if (active) setError(cause.message) })
    return () => { active = false }
  }, [base, selected?.id, selected?.completed, selected?.status, selected?.knownUsd, selected?.unknownCalls, selected?.pendingCalls, offset, refresh])
  const estimate = qaEstimate(capabilities, targetModel, turns)
  useEffect(() => {
    let active = true, loading = false, capLoaded = false
    async function load() {
      if (loading) return
      loading = true
      try {
        if (!capLoaded) {
          const cap = await qaRequest(`${base}/capabilities`)
          if (!active) return
          setCapabilities(cap); setUnavailable(""); capLoaded = true
        }
        const list = await qaRequest(`${base}/runs`)
        if (active) setRuns(list)
      } catch (cause) {
        if (active) {
          if (cause.status === 404 || cause.status === 403) { setUnavailable(cause.message); setCapabilities(null) }
          else setError(cause.message)
        }
      } finally { loading = false }
    }
    load()
    const timer = setInterval(load, 3000)
    return () => { active = false; clearInterval(timer) }
  }, [base, refresh])
  function change(setter, value) { setter(value); setAcknowledge(false); requestId.current = null }
  async function start() {
    if (busy) return
    setBusy(true); setError("")
    try {
      requestId.current ??= crypto.randomUUID()
      const body = qaStartRequest({ requestId: requestId.current, targetModel, turns, paid, budgetUsd: budget, acknowledgeCost: acknowledge }, capabilities)
      body.snapshotDigest = capabilities.snapshot.digest
      const run = await qaRequest(`${base}/runs`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) })
      setRuns(current => [run, ...current.filter(item => item.id !== run.id)])
      setSelectedId(run.id); setOffset(0); setAcknowledge(false); requestId.current = null; setRefresh(value => value + 1)
    } catch (cause) { setError(cause.message) } finally { setBusy(false) }
  }
  async function control(action) {
    setBusy(true); setError("")
    try {
      const run = await qaRequest(`${base}/runs/${selected.id}/${action}`, { method: "POST" })
      setRuns(current => current.map(item => item.id === run.id ? run : item)); setRefresh(value => value + 1)
    } catch (cause) { setError(cause.message) } finally { setBusy(false) }
  }
  async function saveReview(turn) {
    setBusy(true); setError("")
    try {
      await qaRequest(`${base}/runs/${selected.id}/reviews`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ turn, type: reviewTypes[turn] ?? "SETTING_DRIFT", note: reviewNotes[turn] ?? "" }) })
      setReviewNotes(current => ({ ...current, [turn]: "" })); setRefresh(value => value + 1)
    } catch (cause) { setError(cause.message) } finally { setBusy(false) }
  }
  const ongoing = runs.some(run => run.workerAlive)
  return <section className="mb-3 rounded-lg border bg-background p-3" aria-label="실 비용 및 체감 테스트">
    <h3 className="text-sm font-semibold">실 비용 및 체감 테스트</h3>
    <p className="mt-1 text-xs leading-5 text-muted-foreground">현재 포켓의 설정·캐릭터·플레이어·대화·기억을 격리 복사하여 실제 서비스와 같은 대화 흐름을 검사합니다. 원본 포켓은 변경하지 않습니다.</p>
    {unavailable ? <p className="mt-3 text-xs text-muted-foreground">{unavailable}</p> : !capabilities ? <p className="mt-3 text-xs text-muted-foreground">실행 환경 확인 중…</p> : <>
      <div className="mt-3 rounded border p-2 text-xs leading-5" aria-label="복사할 원본 설정">
        <p><strong>{capabilities.snapshot.world}</strong></p><p>등장인물: {capabilities.snapshot.characters.join(" · ")}</p>
        <p>플레이어: {capabilities.snapshot.player || "프로필 없음 · 사용자 자유 역할"}</p>
        <p>기존 메시지 {capabilities.snapshot.messages}개 · 기억 {capabilities.snapshot.memories}개</p>
        <p className="text-muted-foreground">복사 후 설정 일치 검사 · 서비스 응답 지침 유지 · 하드캡 보호용 역할별 출력 상한 적용</p>
      </div>
      <p className="mt-3 rounded bg-muted/40 px-2 py-2 text-xs">플레이어 대역·내부 판단: <strong>GPT-5.6 Luna · 고정</strong></p>
      <p className="mt-1 text-xs text-muted-foreground">검사 오류는 기록하고 다음 턴으로 진행합니다. 대상 원문을 유지하며 예산·과금·통신 문제는 중단합니다.</p>
      <div className="mt-3 grid grid-cols-1 gap-3">
        <label className="text-xs">테스트 대상 모델
          <select className={field} value={targetModel} onChange={event => change(setTargetModel, event.target.value)} disabled={busy}>
            {capabilities.targetModels.map(model => <option value={model} key={model}>{qaModelLabel(model)}</option>)}
          </select>
        </label>
        <label className="text-xs">테스트 횟수 · 왕복 턴
          <input className={field} type="number" min="1" max={capabilities.maxTurns} step="1" value={turns} onChange={event => change(setTurns, event.target.value)} disabled={busy} />
          <span className="mt-1 flex flex-wrap gap-1">{[20, 100, 500, 1000].map(count => <button type="button" key={count} disabled={busy} className="rounded border px-2 py-1 text-[11px] hover:bg-muted" onClick={() => change(setTurns, count)}>{count}턴</button>)}</span>
        </label>
        <label className="text-xs">실행 방식
          <select className={field} value={paid ? "paid" : "mock"} onChange={event => change(setPaid, event.target.value === "paid")} disabled={busy}>
            <option value="mock">무료 모의 점검</option>
            <option value="paid" disabled={!capabilities.paidAvailable}>실제 AI 대화 · 유료</option>
          </select>
        </label>
        {paid && <label className="text-xs">절대 초과 금지 예산 · USD
          <input className={field} type="number" min="0.01" step="0.01" placeholder="직접 입력" value={budget} onChange={event => change(setBudget, event.target.value)} disabled={busy} />
        </label>}
      </div>
      {paid && <p className="mt-2 text-xs leading-5 text-muted-foreground">예상액은 지출 보장이 아닙니다. 각 호출의 최대 비용을 예약할 수 있을 때만 전송합니다. 플레이어 기본 4,096 · 대상 {Number(capabilities.budgetPolicy?.outputCaps?.TARGET ?? 10000).toLocaleString()}(기존 응답 분포 기준) · 기억 추출 4,096 · 압축 8,192 · 시간 해석 2,048 출력 토큰 상한을 사용하며, 서비스의 더 낮은 기존 상한은 유지합니다. 상한 도달은 별도 검토하며 자동으로 재호출하지 않습니다.</p>}
      {!capabilities.paidAvailable && <p className="mt-2 text-xs text-muted-foreground">실제 AI 실행: {capabilities.paidUnavailableReason}</p>}
      {paid ? <div className="mt-3 space-y-2 text-xs">
        <p>테스트 운영 예상 비용: {estimate ? `${usd(estimate.low)} ~ ${usd(estimate.high)}` : "가격표 확인 필요"}</p>
        <p>실서비스 예상 비용: {capabilities.perTurnEstimates[targetModel] ? `${usd(Number(capabilities.perTurnEstimates[targetModel].serviceLowUsd) * Number(turns))} ~ ${usd(Number(capabilities.perTurnEstimates[targetModel].serviceHighUsd) * Number(turns))}` : "가격표 확인 필요"}</p>
        <p className="leading-5 text-muted-foreground">현재 설정·기존 응답 분량을 기준으로 한 추정 범위이며 최대 지출액이 아닙니다. 실서비스 비용은 대상 모델·기억 처리, 운영 비용은 플레이어 AI까지 포함합니다. 캐시·응답 길이·보조 호출 횟수에 따라 달라집니다.</p>
        <label className="flex items-start gap-2 leading-5"><input type="checkbox" className="mt-1" checked={acknowledge} onChange={event => setAcknowledge(event.target.checked)} />테스트 전용 역할별 출력 상한과 하드캡 정책을 적용해 실제 유료 API를 호출하는 데 동의합니다.</label>
      </div> : <p className="mt-3 text-xs leading-5 text-muted-foreground">무료 모의 점검은 저장·검색·실행 흐름을 검사합니다. 실제 AI 품질·원가 측정은 유료 모드를 사용하세요. 모의 점검 성공은 서사 품질 합격을 의미하지 않습니다.</p>}
      <Button type="button" className="mt-3 w-full" onClick={start} disabled={busy || ongoing || (paid && (!acknowledge || !(Number(budget) > 0)))}>{busy ? "요청 중…" : "실 비용 및 체감 테스트 시작"}</Button>
      {ongoing && <p className="mt-1 text-xs text-muted-foreground">실행 중인 테스트가 끝나면 새 테스트를 시작할 수 있어요.</p>}
      {runs.length > 0 && <div className="mt-4 border-t pt-3">
        <label className="text-xs">테스트 기록
          <select className={field} value={selected?.id ?? ""} onChange={event => { setSelectedId(event.target.value); setOffset(0) }}>
            {runs.map(run => <option key={run.id} value={run.id}>{qaModelLabel(run.targetModel)} · {run.total}턴 · {qaStatusLabels[run.status] ?? run.status} · {run.id.slice(-6)}</option>)}
          </select>
        </label>
        {selected && <div className="mt-3 space-y-3">
          <div className="flex flex-wrap justify-between gap-2 text-xs"><strong role="status">{qaStatusLabels[selected.status] ?? selected.status}</strong><span>{selected.completed} / {selected.total}턴</span></div>
          <progress className="h-2 w-full" max={selected.total} value={selected.completed} aria-label="테스트 진행률" />
          <p className="text-xs">{selected.paid ? "테스트 운영 비용 소계" : "무료 모의 비용"}: <strong>{usd(selected.knownUsd)}</strong>{selected.paid && ` / 최대 ${usd(selected.budgetUsd)}`}{selected.unknownCalls > 0 && ` · ${qaCostStatus(selected.unknownCalls, selected.pendingCalls)}`}</p>
          {selected.paid && selected.budget && <div className="rounded border p-2 text-xs leading-5" aria-label="예산 예약 및 정산">
            <p>확인된 실제 지출액: {usd(selected.budget.actualUsd)} · 예약 중: {usd(selected.budget.reservedUsd)}</p>
            <p>사용 가능 잔액: {usd(selected.budget.remainingUsd)}</p>
            <p className="text-muted-foreground">지출에는 진행 중인 턴의 호출도 포함됩니다. 사용 가능 잔액은 지출과 예약액을 뺀 금액입니다.</p>
            <p>최근 계획 호출: {selected.budget.role ?? "계수 대기"} {selected.budget.model ?? ""}</p>
            <p>해당 호출 예상: {usd(selected.budget.expectedUsd)} · 안전 여유: {usd(selected.budget.safetyMarginUsd)}</p>
            <p>안전 여유 포함 예상: {usd(selected.budget.safeExpectedUsd)} · 최대 예약액: {usd(selected.budget.nextMaximumUsd)}</p>
            {selected.budget.inputTokens != null && <p>계수된 입력 {selected.budget.inputTokens} · 출력 상한 {selected.budget.outputCapTokens} · 별도 추론 예약 {selected.budget.additionalThoughtBoundTokens} 토큰</p>}
            {selected.budget.differenceFromExpectedUsd != null && <p>최근 실측−예상 차이: {usd(selected.budget.differenceFromExpectedUsd)}</p>}
            {selected.budget.reason && <p role="status">{selected.budget.reason}</p>}
            {selected.budget.uncertain && <p>미확인 비용의 예약을 유지하며 추가 유료 호출을 차단합니다.</p>}
          </div>}
          {selected.reason && <p className="break-words text-xs leading-5 text-muted-foreground">{selected.reason}</p>}
          <div className="flex flex-wrap gap-2">
            {selected.status === "RUNNING" && <Button type="button" size="sm" variant="outline" disabled={busy} onClick={() => control("pause")}>일시정지</Button>}
            {["PAUSED", "INTERRUPTED"].includes(selected.status) && <Button type="button" size="sm" variant="outline" disabled={busy || selected.workerAlive} onClick={() => control("resume")}>{selected.workerAlive ? "실행기 정리 중…" : "재개"}</Button>}
            {["STARTING", "RUNNING", "PAUSED", "INTERRUPTED", "REVIEW_REQUIRED", "BUDGET_STOP"].includes(selected.status) && <Button type="button" size="sm" variant="outline" disabled={busy} onClick={() => control("stop")}>테스트 중단</Button>}
          </div>
          {(selected.completed > 0 || ["REVIEW_REQUIRED", "BUDGET_STOP", "FAILED", "INTERRUPTED"].includes(selected.status)) && <div className="flex flex-wrap gap-x-3 gap-y-2 text-xs underline underline-offset-4">
            {[ ["json", "전체 원본 JSON"], ["markdown", "검증 보고서"], ["turns", "턴별 CSV"], ["calls", "호출별 CSV"], ["metrics", "분량·시간 CSV"], ["prompts", "설정·프롬프트 ZIP"] ].map(([format, label]) => <a key={format} href={`${base}/runs/${selected.id}/report?format=${format}`} download>{label}</a>)}
          </div>}
          {analysis?.replyCharacters && <div className="space-y-2 rounded border p-2 text-xs leading-5">
            <p>실서비스 확인 비용: <strong>{usd(analysis.serviceUsd)}</strong> · 플레이어 AI: {usd(analysis.playerUsd)}</p>
            <p>테스트 운영 확인 비용: <strong>{usd(analysis.operatingUsd)}</strong>{analysis.unknownCalls > 0 && ` · ${qaCostStatus(analysis.unknownCalls, analysis.pendingCalls)} 금액을 제외한 소계`}</p>
            <p>캐시 적중률: {(analysis.cacheHitRate * 100).toFixed(1)}%</p>
            <p>응답 글자 수: 최소 {analysis.replyCharacters.min} · 중앙 {analysis.replyCharacters.median} · 평균 {analysis.replyCharacters.average.toFixed(1)} · 최대 {analysis.replyCharacters.max}</p>
            <p>저장·기억 처리 포함 왕복 시간: 최소 {(analysis.latencyMillis.min / 1000).toFixed(1)}초 · 중앙 {(analysis.latencyMillis.median / 1000).toFixed(1)}초 · 평균 {(analysis.latencyMillis.average / 1000).toFixed(1)}초 · 최대 {(analysis.latencyMillis.max / 1000).toFixed(1)}초</p>
            <div className="rounded border p-2">
              <p className="font-semibold">QA 본문 준비·후속 처리 시간</p>
              {[["preGenerationMs","생성 경로 진입 전 대기"],["targetDispatchMs","HTTP 호출 직전까지 대기 (QA 사전검증 포함)"]].map(([field,label])=>{
                const stat=analysis.chatTiming?.[field]
                return <p key={field}>{label}: {stat?.count > 0 ? `최소 ${qaTimingSeconds(stat.min)} · 최대 ${qaTimingSeconds(stat.max)} · 평균 ${qaTimingSeconds(stat.average)} · ${stat.count}턴` : "미측정"}</p>
              })}
              {[["firstTokenMs","첫 본문 조각 준비"],["bodyReadyMs","전체 본문 준비"],["reviewCompleteMs","연속성 검사 완료"],["processingCompleteMs","저장·기억 포함 처리 완료"]].map(([field,label])=>{
                const stat=analysis.chatTiming?.[field]
                return <p key={field}>{label}: {stat?.count > 0 ? `최소 ${(stat.min/1000).toFixed(2)}초 · 최대 ${(stat.max/1000).toFixed(2)}초 · 평균 ${(stat.average/1000).toFixed(2)}초 · ${stat.count}턴` : "미측정 · 업데이트 후 새 실행부터 기록"}</p>
              })}
              <p className="text-muted-foreground">QA 대상 요청 시작 기준 서버 단계 시간입니다. 실제 브라우저 수신·표시 시간과는 다릅니다.</p>
              {analysis.failedAttemptTiming?.firstTokenMs != null && <p>중단 요청 첫 본문 조각: {qaTimingSeconds(analysis.failedAttemptTiming.firstTokenMs)} · 완료 평균에서 제외</p>}
              <details><summary className="cursor-pointer">생성 전 병목 상세 · 평균 소요 시간순</summary>
                <p className="text-muted-foreground">아래 값은 각 작업 소요 시간입니다. 기억 조회·토큰 계수는 상위 작업에 포함되므로 표 전체를 합산하지 마세요. 0초는 실제 측정, 미측정은 기록 없음입니다.</p>
                {qaPreparationRows(analysis.chatTiming?.stages).length ? qaPreparationRows(analysis.chatTiming?.stages).map(row=><p key={row.key}>{row.label}: 평균 {qaTimingSeconds(row.stats.average)} · 최소 {qaTimingSeconds(row.stats.min)} · 최대 {qaTimingSeconds(row.stats.max)} · {row.stats.count}턴 / {row.stats.calls}회{row.stats.failedCalls > 0 && ` · 실패 ${row.stats.failedCalls}회`}</p>) : <p>미측정 · 업데이트 후 새 실행부터 기록</p>}
              </details>
              {analysis.failedAttemptTiming?.source === "QA_SERVER" && <p className="text-muted-foreground">중단 요청 (평균 제외): 본문 준비 {analysis.failedAttemptTiming.bodyReadyMs != null ? `${(analysis.failedAttemptTiming.bodyReadyMs/1000).toFixed(2)}초` : "미측정"} · 검사 완료 {analysis.failedAttemptTiming.reviewCompleteMs != null ? `${(analysis.failedAttemptTiming.reviewCompleteMs/1000).toFixed(2)}초` : "미측정"} · 기억 포함 완료 {analysis.failedAttemptTiming.processingCompleteMs != null ? `${(analysis.failedAttemptTiming.processingCompleteMs/1000).toFixed(2)}초` : "미측정"}</p>}
            </div>
            {analysis.generationMillis?.count > 0 && <p>모델 호출 시간: 최소 {(analysis.generationMillis.min / 1000).toFixed(1)}초 · 중앙 {(analysis.generationMillis.median / 1000).toFixed(1)}초 · 최대 {(analysis.generationMillis.max / 1000).toFixed(1)}초</p>}
            <p>출력 토큰: 최소 {analysis.outputTokens.min} · 중앙 {analysis.outputTokens.median} · 최대 {analysis.outputTokens.max}</p>
            <p>응답 분포: {Object.entries(analysis.replyCharacters.buckets).map(([range, count]) => `${range}자 ${count}회`).join(" / ")}</p>
            {analysis.comparison && <>
              
              <p>완료 {selected.completed}턴 기준 실행 당시 운영 예상: {usd(analysis.comparison.expectedOperatingLowToDate)} ~ {usd(analysis.comparison.expectedOperatingHighToDate)}</p>
              <p>예상 상단 대비 실측 차이: {usd(analysis.comparison.differenceFromHigh)} · 실제 호출 {analysis.comparison.callCount}건</p>
              <p>초기 입력 추정 {analysis.comparison.initialEstimatedInputTokens} / 실측 평균 {analysis.comparison.actualMeanTargetInputTokens.toFixed(0)}토큰 · 기존 출력 기준 {analysis.comparison.initialObservedOutputTokens > 0 ? analysis.comparison.initialObservedOutputTokens : "미측정"} / 실측 평균 {analysis.comparison.actualMeanTargetOutputTokens.toFixed(0)}토큰</p>
              <p className="text-muted-foreground">{analysis.comparison.explanation}</p>
            </>}
            <p className="text-muted-foreground">{analysis.verdict}</p>
            <details><summary className="cursor-pointer">연속성 검사 오류·짧음·반복·응답 상태 검토 {analysis.findings.length}건</summary>
              {analysis.findings.map((finding, i) => <p key={i} className="mt-1">{finding.turn ? `${finding.turn}턴` : "전체"} · {finding.type}: {finding.evidence}</p>)}
            </details>
          </div>}
          {selected.completed > 0 && analysis && <details open><summary className="cursor-pointer text-xs font-medium">전체 대화 · 순서대로 보기</summary>
            <div className="my-2 flex items-center justify-between gap-2 text-xs">
              <button type="button" disabled={offset === 0} onClick={() => setOffset(Math.max(0, offset - 20))}>이전 20턴</button>
              <span>{offset + 1}–{Math.min(offset + 20, conversationTotal)} / {conversationTotal}턴</span>
              <button type="button" disabled={offset + 20 >= conversationTotal} onClick={() => setOffset(offset + 20)}>다음 20턴</button>
            </div>
            <div className="max-h-[36rem] space-y-3 overflow-y-auto">{conversation.map(turn => {
              const metric = analysis?.turns?.find(row => row.turn === turn.number)
              return <article key={turn.number} className="rounded border p-2 text-xs leading-5">
                <p className="font-semibold">{turn.number}턴 · {(turn.latencyMillis / 1000).toFixed(1)}초</p>
                <p className="text-muted-foreground">QA 서버 기준 · 본문 준비 {metric?.bodyReadyMs != null ? `${(metric.bodyReadyMs/1000).toFixed(2)}초` : "미측정"} · 검사 완료 {metric?.reviewCompleteMs != null ? `${(metric.reviewCompleteMs/1000).toFixed(2)}초` : "미측정"} · 기억 포함 완료 {metric?.processingCompleteMs != null ? `${(metric.processingCompleteMs/1000).toFixed(2)}초` : "미측정"}</p>
                <details><summary className="cursor-pointer">이 턴 생성 전 병목</summary>
                  <p>생성 경로 진입 전 {qaTimingSeconds(metric?.preGenerationMs)} · HTTP 호출 직전 {qaTimingSeconds(metric?.targetDispatchMs)}</p>
                  {qaPreparationStages.filter(([key])=>metric?.preparationStages?.[key]).map(([key,label])=><p key={key}>{label}: {qaTimingSeconds(metric.preparationStages[key].durationMs)} · {metric.preparationStages[key].count}회</p>)}
                </details>
                {metric && <p className="text-muted-foreground">입력 {metric.inputTokens} / 출력 {metric.outputTokens}토큰{metric.generationMillis >= 0 && ` · 모델 ${(metric.generationMillis / 1000).toFixed(1)}초`} · 본문 {metric.replyCharacters}자 · 대사 약 {metric.dialogueCharacters} / 나레이션 약 {metric.narrationCharacters}자</p>}
                <p className="mt-1 whitespace-pre-wrap break-words"><strong>플레이어 AI</strong>{"\n"}{turn.input}</p>
                <p className="mt-2 whitespace-pre-wrap break-words"><strong>캐릭터</strong>{"\n"}{turn.outcome.reply}</p>
                <details className="mt-2"><summary className="cursor-pointer">이 턴 검토 기록 남기기</summary>
                  <select className={field} value={reviewTypes[turn.number] ?? "SETTING_DRIFT"} onChange={event => setReviewTypes(current => ({ ...current, [turn.number]: event.target.value }))}>
                    <option value="SETTING_DRIFT">설정 이탈</option><option value="CHARACTER_DRIFT">캐릭터 이탈</option><option value="MEMORY_ERROR">기억 오류</option><option value="APPROPRIATE_SHORT">상황에 적절한 짧은 응답</option><option value="OTHER">기타</option>
                  </select>
                  <textarea className="mt-2 w-full rounded border p-2" maxLength={1000} aria-label={`${turn.number}턴 검토 내용`} value={reviewNotes[turn.number] ?? ""} onChange={event => setReviewNotes(current => ({ ...current, [turn.number]: event.target.value }))} />
                  <Button type="button" size="sm" disabled={busy || !reviewNotes[turn.number]?.trim()} onClick={() => saveReview(turn.number)}>검토 저장</Button>
                  {analysis?.reviews?.filter(review => review.turn === turn.number).map((review, i) => <p key={i}>{review.type}: {review.note}</p>)}
                </details>
                <details><summary className="cursor-pointer text-muted-foreground">기억 주입 및 원본 대화 근거</summary><p className="whitespace-pre-wrap break-words">{metric?.memoryEvidence}</p><pre className="whitespace-pre-wrap break-words">{turn.outcome.evaluationJson}</pre></details>
              </article>
            })}</div>
          </details>}

        </div>}
      </div>}
    </>}
    {error && <p role="alert" className="mt-3 text-xs text-destructive">{error}</p>}
  </section>
}

