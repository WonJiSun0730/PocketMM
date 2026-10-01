import { useEffect, useMemo, useRef, useState } from "react"
import { ArrowLeft, BookOpen, ChevronRight, MessageCircle, Pencil, Plus, Save, Sparkles, Trash2, Users, X } from "lucide-react"

import "./WorldPage.css"

const categories = [
  { id: "ERA_REGION_BACKGROUND", label: "시대 / 지역 / 배경", maxCharacters: 2000, maxEntries: 20 },
  { id: "SOCIETY_CULTURE", label: "사회 / 문화", maxCharacters: 500, maxEntries: 10 },
  { id: "RACE_ORGANIZATION", label: "종족 / 조직", maxCharacters: 1500, maxEntries: 10 },
  { id: "WORLD_RULE", label: "세계 규칙", maxCharacters: 1000, maxEntries: 15 },
  { id: "TERMINOLOGY", label: "용어 / 고유명사", maxCharacters: 500, maxEntries: 20 },
  { id: "HISTORY", label: "세계 사건 / 역사", maxCharacters: 1000, maxEntries: 10 },
]

export default function WorldPage({ onBack, onOpenWorldChat }) {
  const [worlds, setWorlds] = useState([])
  const [selectedWorld, setSelectedWorld] = useState(null)
  const [selectedCategory, setSelectedCategory] = useState(categories[0])
  const [entries, setEntries] = useState([])
  const [loading, setLoading] = useState(true)
  const [message, setMessage] = useState("")
  const [draftReady, setDraftReady] = useState(false)
  const [worldDialog, setWorldDialog] = useState(null)
  const [worldForm, setWorldForm] = useState({ name: "", description: "", introduction: "" })
  const [worldSubmitting, setWorldSubmitting] = useState(false)
  const [systemMode, setSystemMode] = useState(false)
  const [playGuide, setPlayGuide] = useState("")
  const [systems, setSystems] = useState([])
  const [worldCharacters, setWorldCharacters] = useState([])
  const autosaveTimer = useRef(null)
  const loadedLoreRef = useRef(null)
  const editVersionRef = useRef(0)
  const allowLoreAutosaveRef = useRef(false)

  const totalCharacters = useMemo(
    () => entries.reduce((sum, entry) => sum + (entry.title?.length ?? 0) + (entry.content?.length ?? 0), 0),
    [entries]
  )

  useEffect(() => { loadWorlds() }, [])

  useEffect(() => {
    if (!selectedWorld) return
    loadWorldCharacters(selectedWorld.id)
    if (systemMode) loadSystems(selectedWorld.id)
    else loadLore(selectedWorld.id, selectedCategory.id)
  }, [selectedWorld, selectedCategory, systemMode])

  useEffect(() => {
    if (!selectedWorld || !draftReady || !loadedLoreRef.current) return
    const { worldId, categoryId } = loadedLoreRef.current
    const key = draftKey(worldId, categoryId)
    localStorage.setItem(key, JSON.stringify({
      worldName: selectedWorld.name,
      entries,
    }))

    window.clearTimeout(autosaveTimer.current)
    if (allowLoreAutosaveRef.current && totalCharacters <= selectedCategory.maxCharacters) {
      autosaveTimer.current = window.setTimeout(() => saveLore(true, worldId, categoryId, entries), 800)
    }
    return () => window.clearTimeout(autosaveTimer.current)
  }, [entries, draftReady])

  async function loadWorldCharacters(worldId) {
    const response = await fetch(`/api/worlds/${worldId}/characters`, { credentials: "include" })
    if (response.ok) setWorldCharacters(await response.json())
  }

  async function deleteWorld() {
    if (!selectedWorld) return
    if (worldCharacters.length > 0) {
      setMessage("등장인물이 연결된 월드는 삭제할 수 없어요. 먼저 캐릭터의 월드 연결을 해제해 주세요.")
      return
    }
    if (!window.confirm(`"${selectedWorld.name}" 월드를 삭제할까요?`)) return
    const response = await fetch(`/api/worlds/${selectedWorld.id}`, { method: "DELETE", credentials: "include" })
    if (!response.ok) return setMessage("월드를 삭제하지 못했어요.")
    const remaining = worlds.filter((world) => world.id !== selectedWorld.id)
    setWorlds(remaining)
    setSelectedWorld(remaining[0] ?? null)
    if (remaining[0]) localStorage.setItem("pocketmm:selected-world-id", String(remaining[0].id))
    else localStorage.removeItem("pocketmm:selected-world-id")
    setMessage("")
  }

  async function loadSystems(worldId) {
    setMessage("")
    const response = await fetch(`/api/worlds/${worldId}/systems`, { credentials: "include" })
    if (!response.ok) return setMessage("시스템을 불러오지 못했어요.")
    const data = await response.json()
    setPlayGuide(data.playGuide ?? "")
    setSystems(data.systems ?? [])
  }

  async function saveSystems() {
    if (!selectedWorld) return
    const worldResponse = await fetch(`/api/worlds/${selectedWorld.id}`, {
      method: "PUT", headers: { "Content-Type": "application/json" }, credentials: "include",
      body: JSON.stringify({ name: selectedWorld.name, description: selectedWorld.description ?? null, introduction: selectedWorld.introduction?.trim() || null }),
    })
    if (!worldResponse.ok) return setMessage("인트로를 저장하지 못했어요.")
    const savedWorld = await worldResponse.json()
    setSelectedWorld(savedWorld)
    setWorlds((current) => current.map((item) => item.id === savedWorld.id ? savedWorld : item))
    const response = await fetch(`/api/worlds/${selectedWorld.id}/systems`, {
      method: "PUT", headers: { "Content-Type": "application/json" }, credentials: "include",
      body: JSON.stringify({ playGuide, systems }),
    })
    if (!response.ok) return setMessage("시스템을 저장하지 못했어요.")
    const data = await response.json()
    setPlayGuide(data.playGuide ?? "")
    setSystems(data.systems ?? [])
    setMessage("시스템을 저장했어요.")
  }

  function addSystem() {
    if (systems.length >= 5) return
    setSystems((current) => [...current, { name: "", changeCondition: "", minValue: 0, maxValue: 100, states: [] }])
  }

  function updateSystem(index, field, value) {
    setSystems((current) => current.map((item, i) => i === index ? { ...item, [field]: value } : item))
  }

  function addSystemState(index) {
    setSystems((current) => current.map((item, i) => i === index ? { ...item, states: [...(item.states ?? []), { minValue: item.minValue, maxValue: item.maxValue, label: "" }] } : item))
  }

  function updateSystemState(systemIndex, stateIndex, field, value) {
    setSystems((current) => current.map((item, i) => i === systemIndex ? { ...item, states: item.states.map((state, j) => j === stateIndex ? { ...state, [field]: value } : state) } : item))
  }

  async function loadWorlds() {
    setLoading(true)
    try {
      const response = await fetch("/api/worlds", { credentials: "include" })
      if (!response.ok) throw new Error("월드를 불러오지 못했어요.")
      const data = await response.json()
      setWorlds(data)
      if (data.length > 0) {
        const savedWorldId = Number(localStorage.getItem("pocketmm:selected-world-id"))
        setSelectedWorld(data.find((world) => world.id === savedWorldId) ?? data[0])
      }
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
    allowLoreAutosaveRef.current = false
    loadedLoreRef.current = null
    setMessage("")
    const response = await fetch(`/api/worlds/${worldId}/lore/${category}`, { credentials: "include" })
    if (!response.ok) {
      setMessage("세계 설정을 불러오지 못했어요.")
      return
    }
    const data = await response.json()
    const key = draftKey(worldId, category)
    const stored = localStorage.getItem(key)
    if (stored) {
      try {
        const parsed = JSON.parse(stored)
        const storedWorldName = parsed?.worldName
        const storedEntries = Array.isArray(parsed) ? parsed : parsed?.entries
        if (storedWorldName === selectedWorld?.name && Array.isArray(storedEntries)) {
          setEntries(normalizeDraftEntries(storedEntries))
          setMessage("작성 중이던 내용을 복원했어요.")
        } else {
          localStorage.removeItem(key)
          setEntries(normalizeDraftEntries(data.entries ?? []))
        }
      } catch {
        localStorage.removeItem(key)
        setEntries(normalizeDraftEntries(data.entries ?? []))
      }
    } else {
      setEntries(normalizeDraftEntries(data.entries ?? []))
    }
    loadedLoreRef.current = { worldId, categoryId: category }
    setDraftReady(true)
  }

  function normalizeDraftEntries(values) {
    return values.map((entry) => typeof entry === "string" ? { title: "", content: entry } : { title: entry.title ?? "", content: entry.content ?? "" })
  }

  function openCreateWorld() {
    setWorldForm({ name: "", description: "", introduction: "" })
    setWorldDialog("create")
  }

  function openEditWorld() {
    if (!selectedWorld) return
    setWorldForm({ name: selectedWorld.name, description: selectedWorld.description ?? "", introduction: selectedWorld.introduction ?? "" })
    setWorldDialog("edit")
  }

  async function submitWorld(event) {
    event.preventDefault()
    const name = worldForm.name.trim()
    if (!name || worldSubmitting) return
    setWorldSubmitting(true)
    try {
      const editing = worldDialog === "edit"
      const response = await fetch(editing ? `/api/worlds/${selectedWorld.id}` : "/api/worlds", {
        method: editing ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ name, description: worldForm.description.trim() || null, introduction: worldForm.introduction.trim() || null }),
      })
      if (!response.ok) throw new Error(editing ? "월드 정보를 수정하지 못했어요." : "월드를 만들지 못했어요.")
      const world = await response.json()
      if (editing) {
        setWorlds((current) => current.map((item) => item.id === world.id ? world : item))
      } else {
        setWorlds((current) => [world, ...current])
      }
      setSelectedWorld(world)
      setWorldDialog(null)
      setMessage(editing ? "월드 정보를 수정했어요." : "")
    } catch (error) {
      setMessage(error.message)
    } finally {
      setWorldSubmitting(false)
    }
  }

  function markEdited() {
    editVersionRef.current += 1
    allowLoreAutosaveRef.current = true
  }

  function updateEntry(index, field, value) {
    markEdited()
    setEntries((current) => current.map((entry, i) => i === index ? { ...entry, [field]: value } : entry))
  }

  function addEntry() {
    if (entries.length >= selectedCategory.maxEntries) return
    markEdited()
    setEntries((current) => [...current, { title: "", content: "" }])
  }

  function removeEntry(index) {
    markEdited()
    setEntries((current) => current.filter((_, i) => i !== index))
  }

  async function saveLore(silent = false, worldId = selectedWorld?.id, categoryId = selectedCategory.id, values = entries) {
    if (!worldId || values.reduce((sum, entry) => sum + (entry.title?.length ?? 0) + (entry.content?.length ?? 0), 0) > selectedCategory.maxCharacters) return
    const saveVersion = editVersionRef.current
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
      if (editVersionRef.current === saveVersion) {
        const savedEntries = data.entries ?? []
        if (JSON.stringify(savedEntries) === JSON.stringify(values)) {
          localStorage.removeItem(draftKey(worldId, categoryId))
        } else {
          localStorage.setItem(draftKey(worldId, categoryId), JSON.stringify(values))
        }
      }
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
        <button className="world-save" disabled={!selectedWorld || (!systemMode && totalCharacters > selectedCategory.maxCharacters)} onClick={() => systemMode ? saveSystems() : saveLore(false)} type="button">
          <Save size={16} /> 저장
        </button>
      </header>

      <div className="world-maker-layout">
        <aside className="world-list-panel">
          <div className="world-panel-heading"><strong>내 월드</strong><button onClick={openCreateWorld} type="button" aria-label="월드 추가"><Plus size={17} /></button></div>
          {worlds.length === 0 ? (
            <button className="world-empty" onClick={openCreateWorld} type="button"><Sparkles size={20} /><strong>첫 월드 만들기</strong><span>캐릭터들이 살아갈 배경을 만들어 보세요.</span></button>
          ) : (
            <div className="world-list">
              {worlds.map((world) => (
                <button className={selectedWorld?.id === world.id ? "world-list-item active" : "world-list-item"} key={world.id} onClick={() => { setSelectedWorld(world); localStorage.setItem("pocketmm:selected-world-id", String(world.id)) }} type="button">
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
              <div className="world-title-row"><div><span className="world-eyebrow">EDITING</span><div className="world-name-line"><h2>{selectedWorld.name}</h2><button className="world-edit-meta" onClick={openEditWorld} type="button" aria-label="월드 정보 수정"><Pencil size={14} /></button><button className="world-delete" onClick={deleteWorld} type="button" aria-label="월드 삭제"><Trash2 size={14} /></button></div>{selectedWorld.description && <p className="world-private-description">{selectedWorld.description}</p>}</div>{message && <span className="world-message">{message}</span>}</div>
              <div className="world-cast-summary"><Users size={15}/><span>등장인물 {worldCharacters.length}명</span>{worldCharacters.length > 0 && <span className="world-cast-names">{worldCharacters.map((character) => character.name).join(" · ")}</span>}{worldCharacters.length > 0 && <div className="world-cast-actions">{worldCharacters.map((character) => <button key={character.id} type="button" onClick={() => onOpenWorldChat?.(selectedWorld, character)}><MessageCircle size={13}/>{character.name}과 대화</button>)}</div>}</div>
              <div className="world-category-tabs"><button className={systemMode ? "active" : ""} onClick={() => setSystemMode(true)} type="button">시스템</button>
                {categories.map((category) => (
                  <button className={selectedCategory.id === category.id ? "active" : ""} key={category.id} onClick={() => { setSystemMode(false); setSelectedCategory(category) }} type="button">{category.label}</button>
                ))}
              </div>
              {systemMode ? (
                <div className="world-system-editor">
                  <section className="system-play-guide"><label>인트로</label><p>월드에 처음 들어갔을 때 시작되는 장면이에요. <span>{selectedWorld?.introduction?.length ?? 0} / 2000자</span></p><textarea maxLength={2000} value={selectedWorld?.introduction ?? ""} onChange={(event) => setSelectedWorld((current) => current ? { ...current, introduction: event.target.value } : current)} placeholder="이 세계에 들어왔을 때 처음 마주하는 상황과 장면을 작성해 주세요." /><label className="system-section-label">플레이 가이드</label><p>월드 제작자가 자유롭게 수정하는 시스템 안내예요. <span>{playGuide.length} / 1000자</span></p><textarea maxLength={1000} value={playGuide} onChange={(event) => setPlayGuide(event.target.value)} placeholder="상태 체계, 시스템 사용법, 플레이 팁 등을 자유롭게 작성하세요." /></section>
                  <div className="system-list">
                    {systems.map((system, index) => <article className="system-card" key={index}>
                      <div className="system-card-head"><input value={system.name} onChange={(e) => updateSystem(index, "name", e.target.value)} placeholder="시스템 이름 (예: 피에타)" /><button onClick={() => setSystems((current) => current.filter((_, i) => i !== index))} type="button"><X size={16}/></button></div>
                      <label>변동 조건<textarea value={system.changeCondition} onChange={(e) => updateSystem(index, "changeCondition", e.target.value)} placeholder="AI가 이 수치를 언제, 왜, 얼마나 변화시켜야 하는지 적어 주세요." /></label>
                      <div className="system-bounds"><label>하한<input type="number" value={system.minValue} onChange={(e) => updateSystem(index, "minValue", Number(e.target.value))}/></label><label>상한<input type="number" value={system.maxValue} onChange={(e) => updateSystem(index, "maxValue", Number(e.target.value))}/></label></div>
                      <div className="system-states"><strong>수치에 따른 상태</strong>{(system.states ?? []).map((state, stateIndex) => <div className="system-state-row" key={stateIndex}><input type="number" value={state.minValue} onChange={(e) => updateSystemState(index,stateIndex,"minValue",Number(e.target.value))}/><span>~</span><input type="number" value={state.maxValue} onChange={(e) => updateSystemState(index,stateIndex,"maxValue",Number(e.target.value))}/><input value={state.label} onChange={(e) => updateSystemState(index,stateIndex,"label",e.target.value)} placeholder="상태 / 설명"/><button type="button" onClick={() => setSystems((current) => current.map((item,i) => i === index ? {...item,states:item.states.filter((_,j)=>j!==stateIndex)} : item))}><X size={14}/></button></div>)}<button className="add-system-state" type="button" onClick={() => addSystemState(index)}><Plus size={14}/> 상태 추가</button></div>
                    </article>)}
                    <button className="add-lore-entry" disabled={systems.length >= 5} onClick={addSystem} type="button"><Plus size={17}/> 시스템 추가 ({systems.length}/5)</button>
                  </div>
                </div>
              ) : (<>
              <div className="world-limit-row">
                <span>{entries.length} / {selectedCategory.maxEntries} 슬롯</span>
                <span className={totalCharacters > selectedCategory.maxCharacters ? "over" : ""}>{totalCharacters.toLocaleString()} / {selectedCategory.maxCharacters.toLocaleString()}자</span>
              </div>
              <div className="lore-entry-list">
                {entries.map((entry, index) => (
                  <article className="lore-entry" key={index}>
                    <span className="lore-number">{String(index + 1).padStart(2, "0")}</span>
                    <div className="lore-fields"><input className="lore-title-input" value={entry.title} onChange={(event) => updateEntry(index, "title", event.target.value)} placeholder="제목" /><textarea value={entry.content} onChange={(event) => updateEntry(index, "content", event.target.value)} placeholder="이 설정에 필요한 내용만 간단히 적어 주세요." /></div>
                    <button onClick={() => removeEntry(index)} type="button" aria-label="슬롯 삭제"><X size={16} /></button>
                  </article>
                ))}
                {entries.length < selectedCategory.maxEntries && (
                  <button className="add-lore-entry" onClick={addEntry} type="button"><Plus size={17} /> 설정 슬롯 추가</button>
                )}
              </div></>) }
            </>
          )}
        </section>
      </div>

      {worldDialog && <div className="world-dialog-backdrop" onMouseDown={() => setWorldDialog(null)}><form className="world-dialog" onSubmit={submitWorld} onMouseDown={(event) => event.stopPropagation()}><div className="world-dialog-heading"><div><span className="world-eyebrow">{worldDialog === "edit" ? "EDIT WORLD" : "NEW WORLD"}</span><h2>{worldDialog === "edit" ? "세계관 정보 수정" : "새 세계관 만들기"}</h2></div><button type="button" onClick={() => setWorldDialog(null)} aria-label="닫기"><X size={18} /></button></div><label>세계관 이름<input autoFocus maxLength={100} value={worldForm.name} onChange={(event) => setWorldForm((current) => ({ ...current, name: event.target.value }))} placeholder="세계관 이름" /></label><label>설명 <small>AI에게 전달되지 않는 메모예요.</small><textarea value={worldForm.description} onChange={(event) => setWorldForm((current) => ({ ...current, description: event.target.value }))} placeholder="이 세계관을 구분하기 위한 설명이나 개인 메모를 적어 주세요." /></label><div className="world-dialog-actions"><button type="button" onClick={() => setWorldDialog(null)}>취소</button><button className="primary" disabled={!worldForm.name.trim() || worldSubmitting} type="submit">{worldSubmitting ? "처리 중..." : worldDialog === "edit" ? "수정" : "생성"}</button></div></form></div>}
    </main>
  )
}
