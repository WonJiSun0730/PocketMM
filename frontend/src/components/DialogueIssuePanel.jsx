import { useEffect, useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { issueCategories, issueEvidence, issuesToMarkdown, fetchAllIssues } from "@/lib/dialogueIssueExport"

export default function DialogueIssuePanel({ sessionId, target, managerOpen, onClose }) {
  const dialog = useRef(null)
  const [form, setForm] = useState({ category: "OTHER", problem: "", expected: "" })
  const [issues, setIssues] = useState([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")
  const [notice, setNotice] = useState("")
  const open = Boolean(target || managerOpen)

  useEffect(() => {
    setError(""); setNotice("")
    if (open) { if (!dialog.current.open) dialog.current.showModal() }
    else dialog.current.close()
    if (target) setForm({ category: "OTHER", problem: "", expected: "" })
  }, [open, target])

  useEffect(() => {
    if (!managerOpen) return
    let active = true
    setBusy(true)
    fetchAllIssues().then(items => { if (active) setIssues(items) })
      .catch(e => { if (active) setError(e.message) })
      .finally(() => { if (active) setBusy(false) })
    return () => { active = false }
  }, [managerOpen])

  async function save(event) {
    event.preventDefault(); setBusy(true); setError("")
    try {
      const response = await fetch(`/api/worlds/pockets/${sessionId}/messages/${target.id}/issues`, {
        method: "POST", credentials: "include", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...form, variantNo: target.selectedVariantNo ?? 1 }),
      })
      if (!response.ok) throw new Error("문제 기록을 저장하지 못했어요. 답변이 삭제됐다면 다시 선택해주세요.")
      onClose("saved")
    } catch (e) { setError(e.message) }
    finally { setBusy(false) }
  }

  async function remove(issue) {
    if (!window.confirm(`문제 기록 #${issue.id}를 삭제할까요?`)) return
    setBusy(true); setError("")
    try {
      const response = await fetch(`/api/worlds/dialogue-issues/${issue.id}`, { method: "DELETE", credentials: "include" })
      if (!response.ok) throw new Error("문제 기록을 삭제하지 못했어요.")
      setIssues(current => current.filter(item => item.id !== issue.id))
      setNotice("문제 기록을 삭제했어요. 대화 내용은 유지됩니다.")
    } catch (e) { setError(e.message) }
    finally { setBusy(false) }
  }

  async function download(raw = false) {
    setBusy(true); setError("")
    try {
      const items = await fetchAllIssues(); setIssues(items)
      if (!items.length) { setNotice("아직 저장한 문제 기록이 없어요."); return }
      const content = raw ? items.map(item => JSON.stringify({ ...item, evidence: issueEvidence(item), evidenceJson: undefined })).join("\n") + "\n" : issuesToMarkdown(items)
      const blob = new Blob([content], { type: raw ? "application/x-ndjson;charset=utf-8" : "text/markdown;charset=utf-8" })
      const url = URL.createObjectURL(blob)
      const link = document.createElement("a"); link.href = url
      const date = new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date())
      link.download = `PocketMM-문제기록-${date}.${raw ? "jsonl" : "md"}`
      document.body.appendChild(link); link.click(); link.remove()
      setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch (e) { setError(e.message) }
    finally { setBusy(false) }
  }

  return <dialog ref={dialog} aria-labelledby="issue-panel-title" onCancel={event => { event.preventDefault(); if (!busy) onClose() }}
    className="m-auto max-h-[85svh] w-[min(92vw,44rem)] overflow-y-auto rounded-xl border bg-card p-5 text-foreground shadow-xl backdrop:bg-black/40">
    <div className="mb-4 flex items-center justify-between gap-3">
      <h2 id="issue-panel-title" className="text-lg font-semibold">{target ? "문제 기록" : "문제 기록 관리"}</h2>
      <Button type="button" variant="ghost" disabled={busy} onClick={onClose}>닫기</Button>
    </div>
    {error && <p role="alert" className="mb-3 text-sm text-destructive">{error}</p>}
    {notice && <p role="status" className="mb-3 text-sm text-muted-foreground">{notice}</p>}
    {target ? <form onSubmit={save} className="space-y-4">
      <p className="text-sm text-muted-foreground">버전 {target.selectedVariantNo ?? 1}의 입력·답변·장면 상태를 함께 저장합니다.</p>
      <details className="text-sm"><summary>선택한 답변 보기</summary><p className="mt-2 whitespace-pre-wrap">{target.content ?? target.message}</p></details>
      <label className="block text-sm">문제 종류<select value={form.category} onChange={e => setForm({ ...form, category: e.target.value })}
        className="mt-1 block w-full rounded-md border bg-background p-2" disabled={busy}>
        {Object.entries(issueCategories).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select></label>
      <label className="block text-sm">어떤 문제가 있었나요?<Textarea required maxLength={5000} value={form.problem} disabled={busy}
        onChange={e => setForm({ ...form, problem: e.target.value })} className="mt-1" /></label>
      <label className="block text-sm">기대했던 결과<Textarea required maxLength={5000} value={form.expected} disabled={busy}
        onChange={e => setForm({ ...form, expected: e.target.value })} className="mt-1" /></label>
      <Button type="submit" disabled={busy || !form.problem.trim() || !form.expected.trim()}>{busy ? "저장 중..." : "저장"}</Button>
    </form> : <div className="space-y-4">
      <p className="text-sm text-muted-foreground">내가 저장한 모든 포켓의 기록입니다. 해결 여부를 확인한 뒤 직접 삭제하세요.</p>
      <div className="flex flex-wrap gap-2"><Button disabled={busy} onClick={() => download()}>문서 내려받기 (.md)</Button>
        <Button variant="outline" disabled={busy} onClick={() => download(true)}>원본 데이터 (.jsonl)</Button></div>
      <p className="text-sm">{busy ? "처리 중..." : `저장된 기록 ${issues.length}개`}</p>
      {issues.map(issue => { const evidence = issueEvidence(issue); return <section key={issue.id} className="rounded-lg border p-3 text-sm">
        <div className="flex items-center justify-between gap-2"><strong>#{issue.id} · {issueCategories[issue.category]} · 버전 {issue.variantNo}</strong>
          <Button size="sm" variant="ghost" disabled={busy} onClick={() => remove(issue)}>삭제</Button></div>
        <p className="mt-1 text-xs text-muted-foreground">포켓 {issue.sessionId} · {issue.createdAt}</p>
        <p className="mt-2 whitespace-pre-wrap">{issue.problem}</p>
        <p className="mt-2 whitespace-pre-wrap text-muted-foreground">기대 결과: {issue.expected}</p>
        <details className="mt-3"><summary>저장된 입력·답변·상태 보기</summary>
          <p className="mt-2 whitespace-pre-wrap">입력: {evidence.generation?.userInput ?? "미저장"}</p>
          <p className="mt-2 whitespace-pre-wrap">답변: {evidence.reply ?? "미저장"}</p>
          <p className="mt-2 text-xs text-muted-foreground">시각: {evidence.beforeTime ?? "미저장"} → {evidence.afterTime ?? "미저장"}</p>
          {!evidence.generationContextCaptured && <p className="mt-2 text-xs text-muted-foreground">생성 당시 설정이 없는 이전 답변입니다. 입력·직전 대화는 기록 시점의 참고값입니다.</p>}
        </details>
      </section> })}
    </div>}
  </dialog>
}
