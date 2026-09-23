import { useEffect, useRef, useState } from "react"
import { Button } from "@/components/ui/button"

export default function CustomStatePanel({ conversationId }) {
  if (!Number.isSafeInteger(conversationId) || conversationId <= 0) return null
  // 방이 바뀌면 목록·입력·오류뿐 아니라 진행 중인 요청의 수명도 분리한다.
  return <RoomCustomStatePanel key={conversationId} conversationId={conversationId} />
}

function RoomCustomStatePanel({ conversationId }) {
  const [states, setStates] = useState([])
  const [drafts, setDrafts] = useState({})
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState("")
  const [retry, setRetry] = useState(0)
  const [saving, setSaving] = useState({})
  const [errors, setErrors] = useState({})
  const [saved, setSaved] = useState({})
  const requestsRef = useRef(null)

  useEffect(() => {
    const requests = { active: true, controllers: new Set(), pending: new Set() }
    requestsRef.current = requests
    const controller = new AbortController()
    requests.controllers.add(controller)

    async function load() {
      try {
        const response = await fetch(`/api/conversations/${conversationId}/custom-states`, {
          signal: controller.signal,
        })
        if (!response.ok) throw new Error("사용자 정의 상태를 불러오지 못했습니다.")
        const data = await response.json()
        if (!requests.active) return
        setStates(data)
        setDrafts(Object.fromEntries(data.map(state => [state.definitionId, String(state.currentValue)])))
      } catch {
        if (requests.active) setLoadError("사용자 정의 상태를 불러오지 못했습니다. 다시 시도해 주세요.")
      } finally {
        requests.controllers.delete(controller)
        if (requests.active) setLoading(false)
      }
    }
    load()
    return () => {
      requests.active = false
      for (const pending of requests.controllers) pending.abort()
    }
  }, [conversationId, retry])

  async function save(event, state) {
    event.preventDefault()
    const requests = requestsRef.current
    const id = state.definitionId
    if (!requests?.active || requests.pending.has(id)) return
    const draft = drafts[id] ?? ""
    const next = Number(draft)
    if (!draft.trim() || !Number.isFinite(next) || next < state.minValue || next > state.maxValue) {
      setErrors(current => ({ ...current, [id]: "허용 범위 안의 숫자를 입력해 주세요." }))
      return
    }

    requests.pending.add(id)
    const controller = new AbortController()
    requests.controllers.add(controller)
    setSaving(current => ({ ...current, [id]: true }))
    setErrors(current => ({ ...current, [id]: "" }))
    setSaved(current => ({ ...current, [id]: false }))
    try {
      const response = await fetch(`/api/conversations/${conversationId}/custom-states/${id}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ currentValue: next }),
        signal: controller.signal,
      })
      if (!response.ok) {
        throw new Error(response.status === 400
          ? "저장하지 못했습니다. 값의 범위와 현재 프로필을 확인해 주세요."
          : "저장 결과를 확인하지 못했습니다. 연결을 확인하고 다시 조회해 주세요.")
      }
      const snapshot = await response.json()
      if (!requests.active) return
      // 입력값을 확정값으로 가정하지 않고 서버 응답으로만 표시와 초안을 갱신한다.
      setStates(current => current.map(item => item.definitionId === id ? snapshot : item))
      setDrafts(current => ({ ...current, [id]: String(snapshot.currentValue) }))
      setSaved(current => ({ ...current, [id]: true }))
    } catch (error) {
      if (requests.active) setErrors(current => ({ ...current, [id]: error.message || "저장 결과를 확인하지 못했습니다." }))
    } finally {
      requests.pending.delete(id)
      requests.controllers.delete(controller)
      if (requests.active) setSaving(current => ({ ...current, [id]: false }))
    }
  }

  return (
    <details className="shrink-0 border-b px-4 py-2 text-sm sm:px-6">
      <summary className="cursor-pointer font-medium">내 사용자 정의 상태</summary>
      <div className="mt-2 max-h-[30svh] space-y-3 overflow-y-auto pb-1">
        {loading && <p role="status" className="text-muted-foreground">상태를 불러오는 중...</p>}
        {loadError && <div>
          <p role="alert" className="text-destructive">{loadError}</p>
          <Button type="button" variant="outline" size="sm" className="mt-2"
            onClick={() => {
              setLoading(true)
              setLoadError("")
              setRetry(value => value + 1)
            }}>다시 시도</Button>
        </div>}
        {!loading && !loadError && states.length === 0 &&
          <p className="text-muted-foreground">등록된 사용자 정의 상태가 없습니다.</p>}
        {!loading && !loadError && states.map(state => {
          const id = state.definitionId
          const inputId = `custom-state-${conversationId}-${id}`
          return <form key={id} onSubmit={event => save(event, state)} className="space-y-2 rounded-lg border p-3">
            <label htmlFor={inputId} className="block font-medium">{state.name}</label>
            <p className="text-xs text-muted-foreground">
              현재 확정값: {state.currentValue}{state.unit ? ` ${state.unit}` : ""}
              {" · "}범위: {state.minValue} ~ {state.maxValue}
            </p>
            {state.description && <p className="whitespace-pre-wrap break-words text-xs text-muted-foreground">{state.description}</p>}
            <div className="flex flex-wrap items-center gap-2">
              <input id={inputId} type="number" required min={state.minValue} max={state.maxValue} step="any"
                value={drafts[id] ?? ""} disabled={saving[id]} aria-invalid={Boolean(errors[id])}
                onChange={event => {
                  setDrafts(current => ({ ...current, [id]: event.target.value }))
                  setErrors(current => ({ ...current, [id]: "" }))
                  setSaved(current => ({ ...current, [id]: false }))
                }}
                className="h-9 min-w-0 flex-1 rounded-md border border-input bg-background px-3 outline-none focus-visible:border-ring disabled:opacity-50" />
              {state.unit && <span className="text-xs text-muted-foreground">{state.unit}</span>}
              <Button type="submit" variant="outline" size="sm" disabled={saving[id] || !(drafts[id] ?? "").trim()}>
                {saving[id] ? "저장 중..." : "저장"}
              </Button>
            </div>
            {errors[id] && <p role="alert" className="text-xs text-destructive">{errors[id]}</p>}
            {saved[id] && <p role="status" className="text-xs text-muted-foreground">저장했습니다.</p>}
          </form>
        })}
      </div>
    </details>
  )
}
