import { useEffect, useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { issueCategories } from "@/lib/dialogueIssueExport"

const statuses = { NEW: "접수", REVIEWING: "확인 중", RESOLVED: "처리 완료" }

async function request(url, options = {}) {
  const response = await fetch(url, { credentials: "include", ...options })
  if (!response.ok) {
    if (response.status === 429) throw new Error("한 시간에 최대 20건까지 신고할 수 있어요. 잠시 후 다시 시도해주세요.")
    if ([401, 403].includes(response.status)) throw new Error("로그인 상태와 운영자 권한을 확인해주세요.")
    throw new Error("신고를 처리하지 못했어요. 잠시 후 다시 시도해주세요.")
  }
  return response.status === 204 ? null : response.json()
}

export default function BugReportPanel(props) {
  if (!props.reportOpen && !props.inboxOpen) return null
  return <BugReportDialog key={`${props.sessionId}:${props.reportOpen ? props.target?.id ?? "general" : "inbox"}`} {...props} />
}

function BugReportDialog({ sessionId, target, reportOpen, inboxOpen, onClose }) {
  const dialog = useRef(null)
  const [requestId] = useState(() => crypto.randomUUID())
  const [form, setForm] = useState({ category: "OTHER", problem: "", expected: "", shareContext: false })
  const [reports, setReports] = useState([])
  const [detail, setDetail] = useState(null)
  const [filter, setFilter] = useState("")
  const [refreshVersion, setRefreshVersion] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [busy, setBusy] = useState(inboxOpen)
  const [error, setError] = useState("")
  useEffect(() => {
    const element = dialog.current
    element.showModal()
    return () => element.close()
  }, [])

  useEffect(() => {
    if (!inboxOpen) return
    let active = true
    request(`/api/admin/bug-reports${filter ? `?status=${filter}` : ""}`)
      .then(page => { if (active) { setReports(page); setHasMore(page.length === 50) } })
      .catch(e => { if (active) setError(e.message) })
      .finally(() => { if (active) setBusy(false) })
    return () => { active = false }
  }, [inboxOpen, filter, refreshVersion])

  async function submit(event) {
    event.preventDefault()
    if (busy) return
    setBusy(true); setError("")
    try {
      const receipt = await request(`/api/worlds/pockets/${sessionId}/bug-reports`, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...form, requestId,
          messageId: target?.id ?? null, variantNo: target ? target.selectedVariantNo ?? 1 : null }),
      })
      onClose({ savedId: receipt.id })
    } catch (e) { setError(e.message) }
    finally { setBusy(false) }
  }

  async function loadMore() {
    if (busy || !hasMore || !reports.length) return
    setBusy(true); setError("")
    try {
      const page = await request(`/api/admin/bug-reports?before=${reports.at(-1).id}${filter ? `&status=${filter}` : ""}`)
      setReports(current => [...current, ...page]); setHasMore(page.length === 50)
    } catch (e) { setError(e.message) }
    finally { setBusy(false) }
  }

  async function view(report) {
    if (busy) return
    setBusy(true); setError(""); setDetail(null)
    try { setDetail(await request(`/api/admin/bug-reports/${report.id}`)) }
    catch (e) { setError(e.message) }
    finally { setBusy(false) }
  }

  async function updateStatus(status) {
    if (busy || !detail) return
    setBusy(true); setError("")
    try {
      await request(`/api/admin/bug-reports/${detail.id}/status`, {
        method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ status }),
      })
      setDetail(current => ({ ...current, status }))
      setReports(current => current.map(item => item.id === detail.id ? { ...item, status } : item)
        .filter(item => !filter || item.status === filter))
    } catch (e) { setError(e.message) }
    finally { setBusy(false) }
  }

  let evidence = null
  try { evidence = detail?.evidenceJson ? JSON.parse(detail.evidenceJson) : null } catch { /* Show report text even if a legacy snapshot is unavailable. */ }

  return <dialog ref={dialog} aria-labelledby="bug-report-title"
    onCancel={event => { event.preventDefault(); if (!busy) onClose() }}
    className="m-auto max-h-[85svh] w-[min(92vw,44rem)] overflow-y-auto rounded-xl border bg-card p-5 text-foreground shadow-xl backdrop:bg-black/40">
    <div className="mb-4 flex items-center justify-between gap-3">
      <h2 id="bug-report-title" className="text-lg font-semibold">{reportOpen ? "버그 신고" : "운영자 신고함"}</h2>
      <Button type="button" variant="ghost" disabled={busy} onClick={() => onClose()}>닫기</Button>
    </div>
    {error && <p role="alert" className="mb-3 text-sm text-destructive">{error}</p>}
    {reportOpen ? <form onSubmit={submit} className="space-y-4">
      <p className="text-sm text-muted-foreground">신고 내용은 운영자 신고함으로 전달됩니다. 비밀번호나 API 키 등 비밀 정보는 적지 마세요.</p>
      <label className="block text-sm">문제 종류<select value={form.category} disabled={busy}
        onChange={event => setForm(current => ({ ...current, category: event.target.value }))}
        className="mt-1 block w-full rounded-md border bg-background p-2">
        {Object.entries(issueCategories).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select></label>
      <label className="block text-sm">어떤 문제가 있었나요?<Textarea required maxLength={5000} value={form.problem} disabled={busy}
        onChange={event => setForm(current => ({ ...current, problem: event.target.value }))} className="mt-1" /></label>
      <label className="block text-sm">기대했던 결과 (선택)<Textarea maxLength={5000} value={form.expected} disabled={busy}
        onChange={event => setForm(current => ({ ...current, expected: event.target.value }))} className="mt-1" /></label>
      {target && <div className="rounded-lg border p-3 text-sm">
        <label className="flex items-start gap-2"><input type="checkbox" checked={form.shareContext} disabled={busy}
          onChange={event => setForm(current => ({ ...current, shareContext: event.target.checked }))} className="mt-1" />
          선택한 답변과 신고 시점의 바로 앞 사용자 메시지를 운영자에게 함께 보내기</label>
        <p className="mt-2 text-xs text-muted-foreground">선택하지 않으면 작성한 설명과 신고 위치만 보냅니다. 전체 대화나 장기기억은 첨부하지 않습니다.</p>
        <details className="mt-2"><summary>선택한 답변 · 버전 {target.selectedVariantNo ?? 1}</summary>
          <p className="mt-2 whitespace-pre-wrap">{target.content ?? target.message}</p></details>
      </div>}
      <Button type="submit" disabled={busy || !form.problem.trim()}>{busy ? "신고 보내는 중..." : "운영자에게 신고 보내기"}</Button>
    </form> : <div className="space-y-4">
      <p className="text-sm text-muted-foreground">모든 사용자가 제출한 신고입니다. 신고에 첨부된 내용만 열람하며, 열람·상태 변경은 감사 기록에 남습니다.</p>
      <label className="block text-sm">처리 상태<select value={filter} disabled={busy}
        onChange={event => { setBusy(true); setError(""); setReports([]); setDetail(null); setFilter(event.target.value) }} className="ml-2 rounded-md border bg-background p-2">
        <option value="">전체</option>{Object.entries(statuses).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select></label>
      <Button type="button" variant="outline" disabled={busy}
        onClick={() => { setBusy(true); setError(""); setReports([]); setDetail(null); setRefreshVersion(value => value + 1) }}>새로고침</Button>
      {detail && <section className="rounded-lg border bg-muted/20 p-4 text-sm">
        <div className="flex flex-wrap items-center justify-between gap-2"><strong>신고 #{detail.id} · {issueCategories[detail.category]}</strong>
          <select aria-label="신고 처리 상태" value={detail.status} disabled={busy} onChange={event => updateStatus(event.target.value)}
            className="rounded-md border bg-background p-2">{Object.entries(statuses).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></div>
        <p className="mt-2 text-xs text-muted-foreground">신고자 #{detail.ownerMemberId} · 포켓 {detail.sessionId} · {detail.createdAt}</p>
        <p className="mt-3 whitespace-pre-wrap">{detail.problem}</p>
        {detail.expected && <p className="mt-3 whitespace-pre-wrap">기대 결과: {detail.expected}</p>}
        {evidence ? <div className="mt-4 border-t pt-3"><p className="font-medium">사용자가 공유한 대화</p>
          <p className="mt-2 whitespace-pre-wrap">사용자 입력: {evidence.userInputAtReportTime || "없음"}</p>
          <p className="mt-2 whitespace-pre-wrap">선택한 답변: {evidence.selectedReply}</p>
          {evidence.truncated && <p className="mt-2 text-xs text-muted-foreground">긴 메시지의 일부만 첨부되었습니다.</p>}
        </div> : <p className="mt-3 text-xs text-muted-foreground">사용자가 대화 내용을 첨부하지 않았습니다.</p>}
      </section>}
      {!busy && !reports.length && <p className="text-sm text-muted-foreground">해당하는 신고가 없습니다.</p>}
      {reports.map(report => <button type="button" key={report.id} disabled={busy} onClick={() => view(report)}
        className="block w-full rounded-lg border p-3 text-left text-sm hover:bg-accent disabled:opacity-50">
        <strong>#{report.id} · {issueCategories[report.category]} · {statuses[report.status]}</strong>
        <span className="mt-1 block text-xs text-muted-foreground">신고자 #{report.ownerMemberId} · 포켓 {report.sessionId} · {report.createdAt}</span>
        <span className="mt-1 block text-xs text-muted-foreground">{report.sharedContext ? "사용자 동의 대화 첨부" : "대화 첨부 없음"}</span>
      </button>)}
      {busy && <p role="status" className="text-sm text-muted-foreground">처리 중...</p>}
      {hasMore && <Button type="button" variant="outline" disabled={busy} onClick={loadMore}>이전 신고 더 보기</Button>}
    </div>}
  </dialog>
}
