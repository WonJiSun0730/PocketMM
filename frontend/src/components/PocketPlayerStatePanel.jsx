import { useEffect, useState } from "react"
import { ChevronDown, Plus, Trash2 } from "lucide-react"

import { Button } from "@/components/ui/button"

function normalizeDateTimeLocal(value) {
  if (!value) return ""
  return value.length >= 16 ? value.slice(0, 16) : value
}

function EditableList({ title, values, onChange, placeholder }) {
  function update(index, value) {
    onChange(values.map((item, i) => i === index ? value : item))
  }
  function remove(index) {
    onChange(values.filter((_, i) => i !== index))
  }

  return <div>
    <div className="mb-1.5 flex items-center justify-between">
      <span className="text-xs font-medium">{title}</span>
      <Button type="button" size="sm" variant="ghost" className="h-7 px-2"
        onClick={() => onChange([...values, ""])}>
        <Plus className="mr-1 size-3" />추가
      </Button>
    </div>
    {values.length === 0
      ? <p className="rounded-md border border-dashed px-3 py-2 text-xs text-muted-foreground">없음</p>
      : <div className="space-y-1.5">
        {values.map((value, index) => <div key={index} className="flex gap-1.5">
          <input value={value} onChange={event => update(index, event.target.value)}
            placeholder={placeholder}
            className="h-9 min-w-0 flex-1 rounded-md border bg-background px-3 text-sm" />
          <Button type="button" size="icon" variant="ghost" className="size-9 shrink-0"
            onClick={() => remove(index)} aria-label={title + " 항목 삭제"}>
            <Trash2 className="size-3.5" />
          </Button>
        </div>)}
      </div>}
  </div>
}

export default function PocketPlayerStatePanel({ sessionId, onError }) {
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [state, setState] = useState({
    simulatedAt: "",
    location: "",
    conditions: [],
    inventory: [],
  })

  async function load() {
    setLoading(true)
    try {
      const response = await fetch("/api/worlds/pockets/" + sessionId + "/player-state", {
        credentials: "include",
      })
      if (!response.ok) throw new Error("캐릭터 현재 상태를 불러오지 못했어요.")
      const data = await response.json()
      setState({
        simulatedAt: normalizeDateTimeLocal(data.simulatedAt),
        location: data.location ?? "",
        conditions: data.conditions ?? [],
        inventory: data.inventory ?? [],
      })
    } catch (error) {
      onError?.(error.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [sessionId])

  async function save() {
    if (!state.simulatedAt || saving) return
    setSaving(true)
    onError?.("")
    try {
      const response = await fetch("/api/worlds/pockets/" + sessionId + "/player-state", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({
          simulatedAt: state.simulatedAt.length === 16 ? state.simulatedAt + ":00" : state.simulatedAt,
          location: state.location,
          conditions: state.conditions,
          inventory: state.inventory,
        }),
      })
      if (!response.ok) throw new Error("캐릭터 현재 상태를 저장하지 못했어요.")
      const data = await response.json()
      setState({
        simulatedAt: normalizeDateTimeLocal(data.simulatedAt),
        location: data.location ?? "",
        conditions: data.conditions ?? [],
        inventory: data.inventory ?? [],
      })
    } catch (error) {
      onError?.(error.message)
    } finally {
      setSaving(false)
    }
  }

  return <section className="shrink-0 border-b bg-muted/10">
    <button type="button" onClick={() => setOpen(value => !value)}
      className="flex w-full items-center gap-2 px-4 py-2 text-left text-xs font-medium text-muted-foreground sm:px-6">
      내 캐릭터 현재 상태
      <span className="truncate font-normal">
        {loading ? "불러오는 중..." : state.location ? "· " + state.location : ""}
      </span>
      <ChevronDown className={"ml-auto size-3.5 transition-transform " + (open ? "rotate-180" : "")} />
    </button>

    {open && <div className="grid gap-4 border-t px-4 py-3 sm:px-6">
      <label className="grid gap-1.5">
        <span className="text-xs font-medium">현재 시간</span>
        <input type="datetime-local" value={state.simulatedAt}
          onChange={event => setState(current => ({ ...current, simulatedAt: event.target.value }))}
          className="h-9 rounded-md border bg-background px-3 text-sm" />
      </label>

      <label className="grid gap-1.5">
        <span className="text-xs font-medium">현재 위치</span>
        <input value={state.location}
          onChange={event => setState(current => ({ ...current, location: event.target.value }))}
          placeholder="예: QWL 본부 식당"
          className="h-9 rounded-md border bg-background px-3 text-sm" />
      </label>

      <EditableList title="상태" values={state.conditions}
        onChange={conditions => setState(current => ({ ...current, conditions }))}
        placeholder="예: 오른팔 부상, 피로" />

      <EditableList title="소지품" values={state.inventory}
        onChange={inventory => setState(current => ({ ...current, inventory }))}
        placeholder="예: 도그태그, 반지" />

      <div className="flex justify-end">
        <Button type="button" size="sm" onClick={save}
          disabled={loading || saving || !state.simulatedAt}>
          {saving ? "저장 중..." : "현재 상태 저장"}
        </Button>
      </div>
    </div>}
  </section>
}
