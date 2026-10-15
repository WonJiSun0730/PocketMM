import { useEffect, useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import { QA_PERSONA_MODEL, qaEstimate, qaModelLabel, qaRequest, qaStartRequest, qaStatusLabels } from "@/lib/pocketQa"

const usd = value => value == null ? "미확인" : `$${Number(value).toFixed(4)}`
const field = "mt-1 h-9 w-full rounded-md border bg-background px-2 text-sm"

export default function PocketQaPanel({ sessionId }) {
  const base = `/api/worlds/pockets/${sessionId}/qa`
  const [capabilities, setCapabilities] = useState(null)
  const [unavailable, setUnavailable] = useState("")
  const [targetModel, setTargetModel] = useState(QA_PERSONA_MODEL)
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
  const selected = runs.find(run => run.id === selectedId) ?? runs[0]
  const estimate = qaEstimate(capabilities, targetModel, turns)
  useEffect(() => {
    let active = true, loading = false
    async function load() {
      if (loading) return
      loading = true
      try {
        const cap = await qaRequest(`${base}/capabilities`)
        if (!active) return
        setCapabilities(cap); setUnavailable("")
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
      const run = await qaRequest(`${base}/runs`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) })
      setRuns(current => [run, ...current.filter(item => item.id !== run.id)])
      setSelectedId(run.id); setAcknowledge(false); requestId.current = null; setRefresh(value => value + 1)
    } catch (cause) { setError(cause.message) } finally { setBusy(false) }
  }
  async function control(action) {
    setBusy(true); setError("")
    try {
      const run = await qaRequest(`${base}/runs/${selected.id}/${action}`, { method: "POST" })
      setRuns(current => current.map(item => item.id === run.id ? run : item)); setRefresh(value => value + 1)
    } catch (cause) { setError(cause.message) } finally { setBusy(false) }
  }
  const ongoing = runs.some(run => run.workerAlive)
  return <section className="mb-3 rounded-lg border bg-background p-3" aria-label="자동 기억 테스트">
    <h3 className="text-sm font-semibold">자동 기억 테스트</h3>
    <p className="mt-1 text-xs leading-5 text-muted-foreground">별도 QA 포켓과 합성 인물로 대화·기억 기능을 검사합니다.</p>
    {unavailable ? <p className="mt-3 text-xs text-muted-foreground">{unavailable}</p> : !capabilities ? <p className="mt-3 text-xs text-muted-foreground">실행 환경 확인 중…</p> : <>
      <p className="mt-3 rounded bg-muted/40 px-2 py-2 text-xs">테스트용 봇: <strong>GPT-5.6 Luna · 고정</strong></p>
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
        {paid && <label className="text-xs">최대 사용 비용 · USD
          <input className={field} type="number" min="0.01" step="0.01" placeholder="직접 입력" value={budget} onChange={event => change(setBudget, event.target.value)} disabled={busy} />
        </label>}
      </div>
      {!capabilities.paidAvailable && <p className="mt-2 text-xs text-muted-foreground">실제 AI 실행: {capabilities.paidUnavailableReason}</p>}
      {paid ? <div className="mt-3 space-y-2 text-xs">
        <p>참고 예상 비용: {estimate ? `${usd(estimate.low)} ~ ${usd(estimate.high)}` : "가격표 확인 필요"}</p>
        <p className="leading-5 text-muted-foreground">봇·기억 추출 포함, 캐시 할인 미반영 추정입니다. 입력 길이와 응답에 따라 달라집니다.</p>
        <label className="flex items-start gap-2 leading-5"><input type="checkbox" className="mt-1" checked={acknowledge} onChange={event => setAcknowledge(event.target.checked)} />선택한 모델·횟수·예산으로 실제 유료 API를 호출하는 데 동의합니다.</label>
      </div> : <p className="mt-3 text-xs leading-5 text-muted-foreground">무료 모의 점검은 저장·검색·실행 흐름을 검사합니다. 실제 AI 품질·원가 측정은 유료 모드를 사용하세요.</p>}
      <Button type="button" className="mt-3 w-full" onClick={start} disabled={busy || ongoing || (paid && (!acknowledge || !(Number(budget) > 0)))}>{busy ? "요청 중…" : "자동 테스트 시작"}</Button>
      {ongoing && <p className="mt-1 text-xs text-muted-foreground">실행 중인 테스트가 끝나면 새 테스트를 시작할 수 있어요.</p>}
      {runs.length > 0 && <div className="mt-4 border-t pt-3">
        <label className="text-xs">테스트 기록
          <select className={field} value={selected?.id ?? ""} onChange={event => setSelectedId(event.target.value)}>
            {runs.map(run => <option key={run.id} value={run.id}>{qaModelLabel(run.targetModel)} · {run.total}턴 · {qaStatusLabels[run.status] ?? run.status} · {run.id.slice(-6)}</option>)}
          </select>
        </label>
        {selected && <div className="mt-3 space-y-3">
          <div className="flex flex-wrap justify-between gap-2 text-xs"><strong role="status">{qaStatusLabels[selected.status] ?? selected.status}</strong><span>{selected.completed} / {selected.total}턴</span></div>
          <progress className="h-2 w-full" max={selected.total} value={selected.completed} aria-label="테스트 진행률" />
          <p className="text-xs">{selected.paid ? "확인된 비용 소계" : "무료 모의 비용"}: <strong>{usd(selected.knownUsd)}</strong>{selected.paid && ` / 최대 ${usd(selected.budgetUsd)}`}{selected.unknownCalls > 0 && ` · 비용 미확인 ${selected.unknownCalls}건`}</p>
          {selected.reason && <p className="break-words text-xs leading-5 text-muted-foreground">{selected.reason}</p>}
          <div className="flex flex-wrap gap-2">
            {selected.status === "RUNNING" && <Button type="button" size="sm" variant="outline" disabled={busy} onClick={() => control("pause")}>일시정지</Button>}
            {["PAUSED", "INTERRUPTED"].includes(selected.status) && <Button type="button" size="sm" variant="outline" disabled={busy || selected.workerAlive} onClick={() => control("resume")}>{selected.workerAlive ? "실행기 정리 중…" : "재개"}</Button>}
            {["STARTING", "RUNNING", "PAUSED", "INTERRUPTED", "REVIEW_REQUIRED", "BUDGET_STOP"].includes(selected.status) && <Button type="button" size="sm" variant="outline" disabled={busy} onClick={() => control("stop")}>테스트 중단</Button>}
          </div>
          {selected.completed > 0 && <div className="flex flex-wrap gap-x-3 gap-y-2 text-xs underline underline-offset-4">
            {[ ["json", "전체 원본 JSON"], ["markdown", "검증 보고서"], ["turns", "턴별 CSV"], ["calls", "호출별 CSV"] ].map(([format, label]) => <a key={format} href={`${base}/runs/${selected.id}/report?format=${format}`} download>{label}</a>)}
          </div>}
          {selected.recentTurns.length > 0 && <details><summary className="cursor-pointer text-xs font-medium">최근 대화와 기억 근거 · 최대 10턴</summary>
            <div className="mt-2 max-h-96 space-y-3 overflow-y-auto">{selected.recentTurns.map(turn => <article key={turn.number} className="rounded border p-2 text-xs leading-5">
              <p className="font-semibold">{turn.number}턴 · {(turn.latencyMillis / 1000).toFixed(1)}초</p>
              <p className="mt-1 whitespace-pre-wrap break-words"><strong>Luna 봇{selected.paid ? "" : " · 모의"}</strong>{"\n"}{turn.input}</p>
              <p className="mt-2 whitespace-pre-wrap break-words"><strong>캐릭터{selected.paid ? "" : " · 모의"}</strong>{"\n"}{turn.reply}</p>
              <details className="mt-2"><summary className="cursor-pointer text-muted-foreground">검색·저장 기억 근거</summary>
                <p className="mt-1 text-muted-foreground">검색된 기억</p><pre className="whitespace-pre-wrap break-words text-[11px]">{JSON.stringify(turn.recalledMemories, null, 2)}</pre>
                <p className="mt-2 text-muted-foreground">저장된 기억</p>{turn.memories.map((memory, index) => <p className="mt-1 break-words" key={index}>{memory}</p>)}
              </details>
            </article>)}</div>
          </details>}
        </div>}
      </div>}
    </>}
    {error && <p role="alert" className="mt-3 text-xs text-destructive">{error}</p>}
  </section>
}
