import { useEffect, useState } from "react"
import { ChevronDown, Settings2 } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import PocketBackgroundPanel from "@/components/PocketBackgroundPanel"

export default function PocketSettingsPanel({ sessionId, location, simulatedAt, onError }) {
  const empty = { userNote: "", playGuide: "", noControlPlayer: false, aiProvider: "GEMINI", aiModel: "" }
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(empty)
  const [draft, setDraft] = useState(empty)

  useEffect(() => {
    let active = true
    setLoading(true)
    fetch(`/api/worlds/pockets/${sessionId}/settings`, { credentials: "include" })
      .then(async response => {
        if (!response.ok) throw new Error("Pocket 설정을 불러오지 못했어요.")
        return response.json()
      })
      .then(data => {
        if (!active) return
        const next = {
          userNote: data.userNote ?? "",
          playGuide: data.playGuide ?? "",
          noControlPlayer: Boolean(data.noControlPlayer),
          aiProvider: data.aiProvider ?? "GEMINI",
          aiModel: data.aiModel ?? "",
        }
        setSaved(next)
        setDraft(next)
      })
      .catch(error => { if (active) onError?.(error.message) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [sessionId])

  const dirty = JSON.stringify(saved) !== JSON.stringify(draft)

  async function save() {
    if (saving || !dirty) return
    setSaving(true)
    onError?.("")
    try {
      const response = await fetch(`/api/worlds/pockets/${sessionId}/settings`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify(draft),
      })
      if (!response.ok) throw new Error("Pocket 설정을 저장하지 못했어요.")
      const data = await response.json()
      const next = {
        userNote: data.userNote ?? "",
        playGuide: data.playGuide ?? "",
        noControlPlayer: Boolean(data.noControlPlayer),
        aiProvider: data.aiProvider ?? "GEMINI",
        aiModel: data.aiModel ?? "",
      }
      setSaved(next)
      setDraft(next)
    } catch (error) {
      onError?.(error.message)
    } finally {
      setSaving(false)
    }
  }

  return <section className="shrink-0 border-b bg-background">
    <button type="button" onClick={() => setOpen(value => !value)}
      className="flex w-full items-center gap-2 px-4 py-2.5 text-left text-xs font-medium text-muted-foreground transition-colors hover:bg-muted/40 hover:text-foreground sm:px-6"
      aria-expanded={open}>
      <Settings2 className="size-3.5" />
      Pocket 설정
      {saved.noControlPlayer && <span className="rounded-full bg-accent px-2 py-0.5 text-[10px] text-accent-foreground">플레이어 조종 금지</span>}
      <ChevronDown className={`ml-auto size-3.5 transition-transform ${open ? "rotate-180" : ""}`} />
    </button>

    {open && <div className="border-t px-4 py-4 sm:px-6">
      {loading ? <p className="text-xs text-muted-foreground">설정을 불러오는 중...</p> :
        <div className="mx-auto grid max-w-2xl gap-5">
          <PocketBackgroundPanel sessionId={sessionId} location={location} simulatedAt={simulatedAt} />
          <div className="grid gap-3 rounded-lg border bg-card p-3.5 sm:grid-cols-2">
            <label className="grid gap-1.5">
              <span className="text-sm font-medium">텍스트 AI 제공자</span>
              <span className="text-xs text-muted-foreground">대화·시간 해석·기억 추출·임베딩·압축·부재 서사에 적용돼요. OpenAI 오류 시 Gemini로 전환하지 않아요.</span>
              <select value={draft.aiProvider}
                onChange={event => setDraft(current => ({
                  ...current,
                  aiProvider: event.target.value,
                  aiModel: event.target.value === "OPENAI" ? "gpt-5.6-luna" : "",
                }))}
                className="h-9 rounded-md border bg-background px-3 text-sm">
                <option value="GEMINI">Gemini</option>
                <option value="OPENAI">OpenAI</option>
              </select>
            </label>
            <label className="grid gap-1.5">
              <span className="text-sm font-medium">모델</span>
              <span className="text-xs text-muted-foreground">API 안에서 사용할 모델을 선택해요.</span>
              {draft.aiProvider === "OPENAI"
                ? <select value={draft.aiModel || "gpt-5.6-luna"}
                    onChange={event => setDraft(current => ({ ...current, aiModel: event.target.value }))}
                    className="h-9 rounded-md border bg-background px-3 text-sm">
                    <option value="gpt-5.6-luna">GPT-5.6 Luna · 저비용</option>
                    <option value="gpt-5.6-terra">GPT-5.6 Terra · 균형</option>
                    <option value="gpt-5.6-sol">GPT-5.6 Sol · 고품질</option>
                  </select>
                : <div className="flex h-9 items-center rounded-md border bg-muted/30 px-3 text-sm text-muted-foreground">서버 기본 Gemini 모델</div>}
            </label>
          </div>

          <label className="grid gap-1.5">
            <span className="text-sm font-medium">유저 노트</span>
            <span className="text-xs text-muted-foreground">이 Pocket에서 AI가 계속 참고할 메모예요.</span>
            <Textarea value={draft.userNote}
              onChange={event => setDraft(current => ({ ...current, userNote: event.target.value }))}
              placeholder="현재 에피소드에서 계속 기억할 내용을 적어주세요."
              className="min-h-24 resize-y bg-card" />
          </label>

          <label className="grid gap-1.5">
            <span className="text-sm font-medium">추가 플레이 가이드</span>
            <span className="text-xs text-muted-foreground">World 공통 가이드는 유지하고, 이 Pocket에서만 지침을 덧붙여요.</span>
            <Textarea value={draft.playGuide}
              onChange={event => setDraft(current => ({ ...current, playGuide: event.target.value }))}
              placeholder="이 Pocket에서만 적용할 진행 방식이나 문체 지침을 적어주세요."
              className="min-h-24 resize-y bg-card" />
          </label>

          <div className="flex items-start justify-between gap-4 rounded-lg border bg-card px-3.5 py-3">
            <div className="min-w-0">
              <p className="text-sm font-medium">플레이어 캐릭터 조종 금지</p>
              <p className="mt-1 text-xs leading-5 text-muted-foreground">AI가 플레이어의 대사, 행동, 생각, 감정, 결정과 이동을 대신 만들지 않아요.</p>
            </div>
            <button type="button" role="switch" aria-checked={draft.noControlPlayer}
              onClick={() => setDraft(current => ({ ...current, noControlPlayer: !current.noControlPlayer }))}
              className={`relative mt-0.5 h-6 w-11 shrink-0 rounded-full border transition-colors ${draft.noControlPlayer ? "border-primary bg-primary" : "border-input bg-muted"}`}>
              <span className={`absolute top-0.5 size-4 rounded-full bg-background shadow-sm transition-transform ${draft.noControlPlayer ? "translate-x-5" : "translate-x-1"}`} />
              <span className="sr-only">플레이어 캐릭터 조종 금지</span>
            </button>
          </div>

          <div className="flex items-center justify-end gap-2">
            {dirty && <Button type="button" variant="ghost" size="sm" onClick={() => setDraft(saved)} disabled={saving}>되돌리기</Button>}
            <Button type="button" size="sm" onClick={save} disabled={saving || !dirty}>{saving ? "저장 중..." : "설정 저장"}</Button>
          </div>
        </div>}
    </div>}
  </section>
}
