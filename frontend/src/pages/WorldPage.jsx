import { useEffect, useMemo, useRef, useState } from "react"
import { ArrowLeft, BookOpen, ChevronRight, Plus, Save, Sparkles, X } from "lucide-react"

import "./WorldPage.css"

const categories = [
  { id: "ERA_REGION_BACKGROUND", label: "시대 / 지역 / 배경", maxCharacters: 2000, maxEntries: 20 },
  { id: "SOCIETY_CULTURE", label: "사회 / 문화", maxCharacters: 500, maxEntries: 10 },
  { id: "RACE_ORGANIZATION", label: "종족 / 조직", maxCharacters: 1500, maxEntries: 10 },
  { id: "WORLD_RULE", label: "세계 규칙", maxCharacters: 1000, maxEntries: 15 },
  { id: "TERMINOLOGY", label: "용어 / 고유명사", maxCharacters: 500, maxEntries: 20 },
  { id: "HISTORY", label: "세계 사건 / 역사", maxCharacters: 1000, maxEntries: 10 },
]

export default function WorldPage({ onBack }) {
  const [worlds, setWorlds] = useState([])
  const [selectedWorld, setSelectedWorld] = useState(null)
  const [selectedCategory, setSelectedCategory] = useState(categories[0])
  const [entries, setEntries] = useState([])
  const [loading, setLoading] = useState(true)
  const [message, setMessage] = useState("")
  const [draftReady, setDraftReady] = useState(false)
  const autosaveTimer = useRef(null)
  const loadedLoreRef = useRef(null)

  const totalCharacters = useMemo(
    () => entries.reduce((sum, entry) => sum + entry.length, 0),
    [entries]
  )

  useEffect(() => { loadWorlds() }, [])

  useEffect(() => {
    if (selectedWorld) loadLore(selectedWorld.id, selectedCategory.id)
  }, [selectedWorld, selectedCategory])

  useEffect(() => {
    if (!selectedWorld || !draftReady || !loadedLoreRef.current) return
    const { worldId, categoryId } = loadedLoreRef.current
    const key = draftKey(worldId, categoryId)
    localStorage.setItem(key, JSON.stringify(entries))

    window.clearTimeout(autosaveTimer.current)
    if (totalCharacters <= selectedCategory.maxCharacters) {
      autosaveTimer.current = window.setTimeout(() => saveLore(true, worldId, categoryId, entries), 800)
    }
    return () => window.clearTimeout(autosaveTimer.current)
  }, [entries, draftReady])

  async function loadWorlds() {
    setLoading(true)
    try {
      const response = await fetch("/api/worlds", { credentials: "include" })
      if (!response.ok) throw new Error("월드를 불러오지 못했어요.")
      const data = await response.json()
      setWorlds(data)
      if (data.length > 0) setSelectedWorld(data[0])
    } catch (error) {
      setMessage(error.message)
    } finally {
      setLoading(false)
    }
  }

  function draftKey(worldId, category) {
    return `pocketmm:world-lore-draft:${worldId}:${category}`
  }

  async function loadLore(worldId, category) {
    setDraftReady(false)
    loadedLoreRef.current = null
    setMessage("")
    const response = await fetch(`/api/worlds/${worldId}/lore/${category}`, { credentials: "include" })
    if (!response.ok) {
      setMessage("세계 설정을 불러오지 못했어요.")
      return
    }
    const data = await response.json()
    const stored = localStorage.getItem(draftKey(worldId, category))
    if (stored) {
      try {
        setEntries(JSON.parse(stored))
        setMessage("작성 중이던 내용을 복원했어요.")
      } catch {
        setEntries(data.entries ?? [])
      }
    } else {
      setEntries(data.entries ?? [])
    }
    loadedLoreRef.current = { worldId, categoryId: category }
    setDraftReady(true)
  }

  async function createWorld() {
    const name = window.prompt("새 월드의 이름을 입력해 주세요.")
    if (!name?.trim()) return
    const response = await fetch("/api/worlds", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ name: name.trim(), description: null }),
    })
    if (!response.ok) {
      setMessage("월드를 만들지 못했어요.")
      return
    }
    const world = await response.json()
    setWorlds((current) => [world, ...current])
    setSelectedWorld(world)
  }

  function updateEntry(index, value) {
    setEntries((current) => current.map((entry, i) => i === index ? value : entry))
  }

  function addEntry() {
    if (entries.length >= selectedCategory.maxEntries) return
    setEntries((current) => [...current, ""])
  }

  function removeEntry(index) {
    setEntries((current) => current.filter((_, i) => i !== index))
  }

  async function saveLore(silent = false, worldId = selectedWorld?.id, categoryId = selectedCategory.id, values = entries) {
    if (!worldId || values.reduce((sum, entry) => sum + entry.length, 0) > selectedCategory.maxCharacters) return
    if (!silent) setMessage("")
    try {
      const response = await fetch(
        `/api/worlds/${worldId}/lore/${categoryId}`,
        {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          credentials: "include",
          body: JSON.stringify({ entries: values }),
        }
      )
      if (!response.ok) throw new Error("세계 설정을 저장하지 못했어요.")
      const data = await response.json()
      setEntries(data.entries ?? [])
      localStorage.removeItem(draftKey(worldId, categoryId))
      if (!silent) setMessage("저장했어요.")
    } catch (error) {
      setMessage(error.message)
    }
  }

  if (loading) {
    return <main className="world-loading">월드를 불러오는 중...</main>
  }

  return (
    <main className="world-maker">
      <header className="world-maker-header">
        <button className="world-back" onClick={onBack} type="button"><ArrowLeft size={18} /> NOW</button>
        <div>
          <span className="world-eyebrow">WORLD MAKING</span>
          <h1>작은 세계를 정리해요</h1>
          <p>필요한 설정만 짧은 슬롯으로 나눠 두면, 관련 장면에서 꺼내 쓸 수 있어요.</p>
        </div>
        <button className="world-save" disabled={!selectedWorld || totalCharacters > selectedCategory.maxCharacters} onClick={() => saveLore(false)} type="button">
          <Save size={16} /> 저장
        </button>
      </header>

      <div className="world-maker-layout">
        <aside className="world-list-panel">
          <div className="world-panel-heading"><strong>내 월드</strong><button onClick={createWorld} type="button" aria-label="월드 추가"><Plus size={17} /></button></div>
          {worlds.length === 0 ? (
            <button className="world-empty" onClick={createWorld} type="button"><Sparkles size={20} /><strong>첫 월드 만들기</strong><span>캐릭터들이 살아갈 배경을 만들어 보세요.</span></button>
          ) : (
            <div className="world-list">
              {worlds.map((world) => (
                <button className={selectedWorld?.id === world.id ? "world-list-item active" : "world-list-item"} key={world.id} onClick={() => setSelectedWorld(world)} type="button">
                  <BookOpen size={16} /><span><strong>{world.name}</strong><small>{world.description || "설명 없음"}</small></span><ChevronRight size={15} />
                </button>
              ))}
            </div>
          )}
        </aside>

        <section className="world-editor">
          {!selectedWorld ? (
            <div className="world-editor-empty"><Sparkles size={26} /><h2>아직 월드가 없어요</h2><p>왼쪽에서 첫 월드를 만들어 주세요.</p></div>
          ) : (
            <>
              <div className="world-title-row"><div><span className="world-eyebrow">EDITING</span><h2>{selectedWorld.name}</h2></div>{message && <span className="world-message">{message}</span>}</div>
              <div className="world-category-tabs">
                {categories.map((category) => (
                  <button className={selectedCategory.id === category.id ? "active" : ""} key={category.id} onClick={() => setSelectedCategory(category)} type="button">{category.label}</button>
                ))}
              </div>
              <div className="world-limit-row">
                <span>{entries.length} / {selectedCategory.maxEntries} 슬롯</span>
                <span className={totalCharacters > selectedCategory.maxCharacters ? "over" : ""}>{totalCharacters.toLocaleString()} / {selectedCategory.maxCharacters.toLocaleString()}자</span>
              </div>
              <div className="lore-entry-list">
                {entries.map((entry, index) => (
                  <article className="lore-entry" key={index}>
                    <span className="lore-number">{String(index + 1).padStart(2, "0")}</span>
                    <textarea value={entry} onChange={(event) => updateEntry(index, event.target.value)} placeholder="이 설정에 필요한 내용만 간단히 적어 주세요." />
                    <button onClick={() => removeEntry(index)} type="button" aria-label="슬롯 삭제"><X size={16} /></button>
                  </article>
                ))}
                {entries.length < selectedCategory.maxEntries && (
                  <button className="add-lore-entry" onClick={addEntry} type="button"><Plus size={17} /> 설정 슬롯 추가</button>
                )}
              </div>
            </>
          )}
        </section>
      </div>
    </main>
  )
}
