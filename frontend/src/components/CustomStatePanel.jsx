import { useEffect, useRef, useState } from "react"
import { Button } from "@/components/ui/button"

export default function CustomStatePanel({ characterId, conversationId }) {
  if (
    !Number.isSafeInteger(characterId) ||
    characterId <= 0 ||
    !Number.isSafeInteger(conversationId) ||
    conversationId <= 0
  ) return null

  return (
    <RoomCustomStatePanel
      key={`${characterId}-${conversationId}`}
      characterId={characterId}
      conversationId={conversationId}
    />
  )
}

function RoomCustomStatePanel({ characterId, conversationId }) {
  const [states, setStates] = useState([])
  const [drafts, setDrafts] = useState({})
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState("")
  const [retry, setRetry] = useState(0)
  const [saving, setSaving] = useState({})
  const [errors, setErrors] = useState({})
  const [saved, setSaved] = useState({})
  const requestsRef = useRef(null)
  const [coreState, setCoreState] = useState(null)
  const [coreDraft, setCoreDraft] = useState({ energy: 70, mood: 70 })
  const [coreSaving, setCoreSaving] = useState(false)

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

  useEffect(() => {
    let cancelled = false

    async function loadCoreState() {
      try {
        const response = await fetch(
          `/api/characters/${characterId}/conversations/${conversationId}/player-state`
        )

        if (!response.ok) {
          throw new Error(`기본 상태 조회 실패 (${response.status})`)
        }

        const data = await response.json()

        if (cancelled) return

        setCoreState(data)
        setCoreDraft({
          energy: data.energy,
          mood: data.mood,
        })
      } catch (error) {
        if (!cancelled) {
          console.error(error)
        }
      }
    }

    loadCoreState()

    return () => {
      cancelled = true
    }
  }, [characterId, conversationId])

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

  async function saveCoreState(event) {
    event.preventDefault()

    const energy = Number(coreDraft.energy)
    const mood = Number(coreDraft.mood)

    if (
      !Number.isFinite(energy) ||
      !Number.isFinite(mood) ||
      energy < 0 ||
      energy > 100 ||
      mood < 0 ||
      mood > 100
    ) {
      return
    }

    setCoreSaving(true)

    try {
      const response = await fetch(
        `/api/characters/${characterId}/conversations/${conversationId}/player-state`,
        {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ energy, mood }),
        }
      )

      if (!response.ok) {
        throw new Error(`기본 상태 저장 실패 (${response.status})`)
      }

      const snapshot = await response.json()

      setCoreState(snapshot)
      setCoreDraft({
        energy: snapshot.energy,
        mood: snapshot.mood,
      })
    } catch (error) {
      console.error(error)
    } finally {
      setCoreSaving(false)
    }
  }

  return (
    <details className="shrink-0 border-b px-4 py-2 text-sm sm:px-6">
      <summary className="cursor-pointer font-medium">내 사용자 정의 상태</summary>
      <div className="mt-2 max-h-[30svh] space-y-3 overflow-y-auto pb-1">
        {coreState && (
          <form
            onSubmit={saveCoreState}
            className="space-y-2 rounded-lg border p-3"
          >
            <p className="font-medium">기본 상태</p>

            <p className="text-xs text-muted-foreground">
              현재 확정값: 에너지 {coreState.energy} · 기분 {coreState.mood}
            </p>

            <div className="grid grid-cols-2 gap-2">
              <label className="space-y-1">
                <span className="text-xs text-muted-foreground">에너지</span>
                <input
                  type="number"
                  min="0"
                  max="100"
                  value={coreDraft.energy}
                  disabled={coreSaving}
                  onChange={event =>
                    setCoreDraft(current => ({
                      ...current,
                      energy: event.target.value,
                    }))
                  }
                  className="h-9 w-full rounded-md border border-input bg-background px-3 outline-none focus-visible:border-ring disabled:opacity-50"
                />
              </label>

              <label className="space-y-1">
                <span className="text-xs text-muted-foreground">기분</span>
                <input
                  type="number"
                  min="0"
                  max="100"
                  value={coreDraft.mood}
                  disabled={coreSaving}
                  onChange={event =>
                    setCoreDraft(current => ({
                      ...current,
                      mood: event.target.value,
                    }))
                  }
                  className="h-9 w-full rounded-md border border-input bg-background px-3 outline-none focus-visible:border-ring disabled:opacity-50"
                />
              </label>
            </div>

            <Button
              type="submit"
              variant="outline"
              size="sm"
              disabled={coreSaving}
            >
              {coreSaving ? "저장 중..." : "기본 상태 저장"}
            </Button>
          </form>
        )}
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
