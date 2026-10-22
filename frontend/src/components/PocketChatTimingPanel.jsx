import {useLayoutEffect, useRef} from "react"
import {observeReplyPaint, timingSeconds, timingStats} from "@/lib/pocketChatTiming"

export function MeasuredPocketReply({timing, sampleId, eligible=true, autoScroll=false, children}) {
  const node = useRef(null)
  useLayoutEffect(() => {
    if (!sampleId || !eligible || !node.current) return
    if (autoScroll) node.current.scrollIntoView({block:"start",behavior:"auto"})
    return observeReplyPaint(node.current,() => timing.displayed(sampleId),() => timing.displayUnavailable(sampleId))
  },[timing,sampleId,eligible,autoScroll])
  return <div ref={node}>{children}</div>
}

const statusLabel = {RUNNING:"처리 중",FINALIZED:"서버 완료 · 화면 갱신 중",COMPLETE:"완료",FAILED:"실패 · 확인 필요",INTERRUPTED:"기록 중단 · 완료 여부 미확인"}
export default function PocketChatTimingPanel({timing,records}) {
  const latest = records[0]
  const fields = [["receivedMs","본문 수신"],["displayMs","화면 표시 추정"],["completeMs","검사·기억 처리 완료"],["readyMs","다음 입력 준비"]]
  function download() {
    const url = URL.createObjectURL(new Blob([JSON.stringify(timing.export(),null,2)],{type:"application/json"}))
    const link = document.createElement("a")
    link.href=url;link.download=`pocket-${timing.export().sessionId}-chat-timing.json`;link.click()
    setTimeout(()=>URL.revokeObjectURL(url),1000)
  }
  return <section className="mb-3 rounded-lg border bg-background p-3" aria-label="실제 채팅 도착 시간">
    <p className="mb-2 text-xs font-semibold">실제 채팅 도착 시간</p>
    <p className="mb-2 text-xs text-muted-foreground">전송 버튼 기준 · 이 브라우저의 Pocket 일반 전송 최근 100건 · 자동 QA 시간과 별도</p>
    {!latest ? <p className="text-xs text-muted-foreground">직접 메시지를 보내면 측정됩니다. 기존 기록은 소급 측정하지 않습니다.</p> : <>
      <p className="mb-2 text-xs">최근 요청: {statusLabel[latest.status] ?? "미확인"} · {new Date(latest.startedAt).toLocaleString("ko-KR")}</p>
      <div className="overflow-x-auto"><table className="w-full text-left text-xs">
        <thead><tr><th>구간</th><th>최근</th><th>최소</th><th>최대</th><th>평균</th><th>완료 표본</th></tr></thead>
        <tbody>{fields.map(([field,label]) => {
          const stats=timingStats(records,field)
          return <tr key={field}><td className="py-1">{label}</td><td>{timingSeconds(latest[field])}</td><td>{timingSeconds(stats?.min)}</td><td>{timingSeconds(stats?.max)}</td><td>{timingSeconds(stats?.average)}</td><td>{stats?.count ?? 0}건</td></tr>
        })}</tbody>
      </table></div>
      <p className="mt-2 text-xs text-muted-foreground">요청 전송 시작 {timingSeconds(latest.sentMs)} · 수신 후 후속 처리 {timingSeconds(latest.completeMs != null && latest.receivedMs != null ? latest.completeMs-latest.receivedMs : null)}</p>
      {latest.displayUnavailable && <p className="mt-1 text-xs text-muted-foreground">탭 비활성으로 화면 표시 추정은 제외했습니다.</p>}
      <details className="mt-2 text-xs"><summary>최근 요청 10건</summary><div className="overflow-x-auto"><table className="mt-2 w-full text-left">
        <thead><tr><th>시각</th><th>상태</th><th>본문 수신</th><th>표시 추정</th><th>처리 완료</th></tr></thead>
        <tbody>{records.slice(0,10).map(row=><tr key={row.id}><td className="py-1">{new Date(row.startedAt).toLocaleTimeString("ko-KR")}</td><td>{statusLabel[row.status] ?? "미확인"}</td><td>{timingSeconds(row.receivedMs)}</td><td>{timingSeconds(row.displayMs)}</td><td>{timingSeconds(row.completeMs)}</td></tr>)}</tbody>
      </table></div></details>
    </>}
    <p className="mt-2 text-[11px] text-muted-foreground">화면 표시는 DOM 반영 후 두 화면 프레임 기준 추정이며 실제 모니터 표시 시각은 아닙니다. 실패·중단 요청은 평균에서 제외합니다. 최근 시도는 이 브라우저에 보관됩니다.</p>
    {!timing.storageAvailable() && <p className="mt-1 text-xs text-destructive">브라우저 저장을 사용할 수 없어 새로고침하면 이번 기록이 사라질 수 있습니다.</p>}
    <div className="mt-2 flex gap-3 text-xs"><button type="button" onClick={download} disabled={!records.length}>시간 기록 JSON 저장</button><button type="button" onClick={()=>timing.clear()} disabled={["RUNNING","FINALIZED"].includes(latest?.status)}>시간 기록 초기화</button></div>
  </section>
}
