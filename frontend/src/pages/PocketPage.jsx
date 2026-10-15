import AiCallUsage from "../components/AiCallUsage";
import PromptTokenUsage from "../components/PromptTokenUsage";
import {enterSendsMessage,chatInputEnvironment} from "@/lib/chatInput"
import { useEffect, useMemo, useRef, useState } from "react"
import { ArrowLeft, ChevronDown, ChevronLeft, ChevronRight, LoaderCircle, Menu, Pencil, RefreshCw, Send, Trash2, Wrench, X } from "lucide-react"

import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Textarea } from "@/components/ui/textarea"
import PlayerProfilePicker from "@/components/PlayerProfilePicker"
import PocketPlayerStatePanel from "@/components/PocketPlayerStatePanel"
import PocketSettingsPanel from "@/components/PocketSettingsPanel"
import DialogueIssuePanel from "@/components/DialogueIssuePanel"
import { collectPocketTestData, downloadPocketTestData } from "@/lib/pocketTestExport"
import usePocketProgress from "@/hooks/usePocketProgress"
import PocketProgressPanel from "@/components/PocketProgressPanel"
import RpText from "@/components/RpText"
import PocketAbsenceLog from "@/components/PocketAbsenceLog"
import TypedPocketReply from "@/components/TypedPocketReply"
import PocketActivityScene from "@/components/PocketActivityScene"
import { resolvePocketSceneModels } from "@/lib/pocketCharacterModels"
import "./PocketPage.css"

function snapshotSceneState(variant) {
  if (!variant?.scenePlayerStateJson) return null
  try {
    const snapshot = JSON.parse(variant.scenePlayerStateJson)
    return {
      simulatedAt: variant.sceneSimulatedAt ?? "",
      location: snapshot.location ?? "",
      inventory: snapshot.inventory ?? [],
      conditions: snapshot.conditions ?? [],
    }
  } catch {
    return null
  }
}

function SceneStateMessage({ state }) {
  const date = state.simulatedAt ? new Date(state.simulatedAt) : null
  const validDate = date && !Number.isNaN(date.getTime())
  const dateText = validDate ? new Intl.DateTimeFormat("ko-KR", { month: "2-digit", day: "2-digit", weekday: "long" }).format(date) : "미설정"
  const timeText = validDate ? new Intl.DateTimeFormat("ko-KR", { hour: "2-digit", minute: "2-digit", hour12: true }).format(date) : "미설정"
  return <section aria-label="현재 장면 상태" className="mx-auto mt-5 w-full max-w-3xl border-t border-border/70 px-4 py-3 text-xs leading-6 text-muted-foreground sm:px-6">
    <div className="mb-1 font-medium text-foreground">💌 현재 장면</div>
    <div className="whitespace-pre-wrap font-mono">
      {`📅: ${dateText}\n⏰: ${timeText}\n🧭: ${state.location || "미설정"}\n👜: ${state.inventory?.length ? state.inventory.join("·") : "없음"}\n🧷: ${state.conditions?.length ? state.conditions.join("·") : "없음"}`}
    </div>
  </section>
}

export default function PocketPage({ pocket, onBack }) {
  const { world, room, session, playerProfile } = pocket
  const base = `/api/worlds/${world.id}/rooms/${room.id}/sessions/${session.id}`
  const [selectedPlayerProfile, setSelectedPlayerProfile] = useState(playerProfile ?? null)
  const [pickerMode, setPickerMode] = useState(false)
  const [changingPlayerProfile, setChangingPlayerProfile] = useState(false)
  const [messages, setMessages] = useState([])
  const [typingTarget,setTypingTarget] = useState(null)
  const [cast, setCast] = useState([])
  const [worldCharacters, setWorldCharacters] = useState([])
  const [systems, setSystems] = useState([])
  const [input, setInput] = useState("")
  const [messageSending, setSending] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")
  const [editing, setEditing] = useState(null)
  const [editText, setEditText] = useState("")
  const [variantsByMessage, setVariantsByMessage] = useState({})
  const [issueTarget, setIssueTarget] = useState(null)
  const [issueManagerOpen, setIssueManagerOpen] = useState(false)
  const [issueNotice, setIssueNotice] = useState("")
  const [exportingTestData, setExportingTestData] = useState(false)
  const [isDeveloper, setIsDeveloper] = useState(false)
  const [devOpen, setDevOpen] = useState(false)
  const [mobileToolsOpen,setMobileToolsOpen] = useState(false)
  const mobileToolsRef = useRef(null)
  useEffect(() => {
    const dialog=mobileToolsRef.current
    if (!dialog) return
    if (mobileToolsOpen) dialog.showModal()
    else dialog.close()
    const media=window.matchMedia("(min-width:1280px)")
    const resized=()=>{if(media.matches)setMobileToolsOpen(false)}
    media.addEventListener("change",resized)
    return ()=>media.removeEventListener("change",resized)
  },[mobileToolsOpen,pickerMode])
  const [devSystemValues, setDevSystemValues] = useState({})
  const [memoryInspector, setMemoryInspector] = useState([])
  const [memoryDiff, setMemoryDiff] = useState(null)
  const [aiUsage, setAiUsage] = useState(null)
  const [aiCalls, setAiCalls] = useState([])
  const [promptDiagnostics, setPromptDiagnostics] = useState(null)
  const [hasOlderMessages, setHasOlderMessages] = useState(false)
  const [loadingOlder, setLoadingOlder] = useState(false)
  const [playerStateRefreshKey, setPlayerStateRefreshKey] = useState(0)
  const [sceneRuntimeState, setSceneRuntimeState] = useState({
    simulatedAt: session.simulatedAt ?? "",
    location: "",
    inventory: [],
    conditions: [],
  })
  const [initialScene, setInitialScene] = useState(session.initialScene ?? null)
  const sceneModels = useMemo(() => resolvePocketSceneModels({
    playerProfile: selectedPlayerProfile,
    worldCharacters,
    cast,
  }), [selectedPlayerProfile, worldCharacters, cast])
  const scrollAreaRef = useRef(null)
  const historySentinelRef = useRef(null)
  const bottomRef = useRef(null)
  const initialScrollDoneRef = useRef(false)
  const historyAnchorIdRef = useRef(null)

  const progress = usePocketProgress(session.id, async () => {
    await refresh()
    refreshPlayerState()
  }, messageSending)
  const sending = messageSending || progress.busy

  async function refreshMemoryInspector() {
    if (!isDeveloper) return
    try {
      const [response, diffResponse] = await Promise.all([
        fetch(`${base}/memory-inspector`, { credentials: "include" }),
        fetch(`${base}/memory-inspector/diff`, { credentials: "include" }),
      ])
      if (response.ok) setMemoryInspector(await response.json())
      if (diffResponse.ok) setMemoryDiff(await diffResponse.json())
    } catch {
      setMemoryInspector([])
      setMemoryDiff(null)
    }
  }

  async function refreshAiUsage() {
    if (!isDeveloper) return
    try {
      const response = await fetch(`/api/worlds/pockets/${session.id}/ai-usage`, { credentials: "include" })
      if (response.ok) setAiUsage(await response.json())
      const [callsResponse, diagnosticResponse] = await Promise.all([
        fetch(`/api/worlds/pockets/${session.id}/ai-usage/calls`, { credentials: "include" }),
        fetch(`/api/worlds/pockets/${session.id}/ai-usage/prompt-diagnostics`, { credentials: "include" }),
      ])
      setAiCalls(callsResponse.ok ? await callsResponse.json() : [])
      const diagnosticText = diagnosticResponse.ok ? await diagnosticResponse.text() : ""
      setPromptDiagnostics(diagnosticText ? JSON.parse(diagnosticText) : null)
    } catch {
      setAiUsage(null)
    }
  }

  async function exportTestData() {
    setExportingTestData(true)
    setError("")
    try {
      const data = await collectPocketTestData({
        base, sessionId: session.id,
        metadata: { worldId: world.id, worldName: world.name, roomId: room.id, roomTitle: room.title },
      })
      downloadPocketTestData(data)
      setMobileToolsOpen(false)
    } catch (cause) {
      setError(cause.message ?? "테스트 데이터 내보내기에 실패했어요.")
    } finally {
      setExportingTestData(false)
    }
  }

  async function loadMessagePage(before = null) {
    const url = before == null
      ? `${base}/messages`
      : `${base}/messages?before=${before}`
    const response = await fetch(url, { credentials: "include" })
    if (!response.ok) throw new Error("포켓의 대화를 불러오지 못했어요.")
    return response.json()
  }

  async function loadPageVariants(page) {
    const characterMessages = page.filter(item => item.role === "CHARACTER")
    const entries = await Promise.all(characterMessages.map(async item => {
      try {
        const response = await fetch(`${base}/messages/${item.id}/variants`, { credentials: "include" })
        return [item.id, response.ok ? await response.json() : []]
      } catch {
        return [item.id, []]
      }
    }))
    setVariantsByMessage(current => ({ ...current, ...Object.fromEntries(entries) }))
  }

  async function refresh({animateReply=false}={}) {
    try {
      const [loadedMessages, castResponse, systemResponse, worldCharactersResponse, sessionResponse] = await Promise.all([
        loadMessagePage(),
        fetch(`${base}/cast`, { credentials: "include" }),
        fetch(`${base}/systems`, { credentials: "include" }),
        fetch(`${base}/characters`, { credentials: "include" }),
        fetch(`/api/worlds/pockets/${session.id}`, { credentials: "include" }),
      ])
      const reply=animateReply ? loadedMessages.findLast(item=>item.role==="CHARACTER" && item.absenceReportId==null) : null
      setTypingTarget(reply ? `${reply.id}:${reply.selectedVariantNo ?? 1}` : null)
      setMessages(loadedMessages)
      await loadPageVariants(loadedMessages)
      setHasOlderMessages(loadedMessages.length === 15)
      if (castResponse.ok) setCast(await castResponse.json())
      if (systemResponse.ok) setSystems(await systemResponse.json())
      if (worldCharactersResponse.ok) setWorldCharacters(await worldCharactersResponse.json())
      if (sessionResponse.ok) setInitialScene((await sessionResponse.json()).initialScene ?? null)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  async function loadOlderMessages() {
    if (loadingOlder || !hasOlderMessages || messages.length === 0) return
    setLoadingOlder(true)
    try {
      historyAnchorIdRef.current = messages[0].id
      const page = await loadMessagePage(messages[0].id)
      setMessages(current => [...page, ...current])
      await loadPageVariants(page)
      setHasOlderMessages(page.length === 15)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoadingOlder(false)
    }
  }

  useEffect(() => {
    initialScrollDoneRef.current = false
    setLoading(true)
    refresh()
  }, [session.id])
  useEffect(() => {
    let active = true
    fetch("/api/auth/me", { credentials: "include" })
      .then(response => response.ok ? response.json() : null)
      .then(member => { if (active) setIsDeveloper(member?.role === "DEVELOPER") })
      .catch(() => { if (active) setIsDeveloper(false) })
    return () => { active = false }
  }, [])
  // Scene rendering must keep receiving runtime state even when the legacy editor is hidden.
  useEffect(() => {
    let active = true
    fetch(`/api/worlds/pockets/${session.id}/player-state`, { credentials: "include" })
      .then(response => response.ok ? response.json() : null)
      .then(state => {
        if (active && state) setSceneRuntimeState({
          simulatedAt: state.simulatedAt ?? "",
          location: state.location ?? "",
          inventory: state.inventory ?? [],
          conditions: state.conditions ?? [],
        })
      })
      .catch(() => {})
    return () => { active = false }
  }, [session.id, playerStateRefreshKey])
  useEffect(() => {
    if (!isDeveloper) {
      setMemoryInspector([])
      setMemoryDiff(null)
      return
    }
    refreshMemoryInspector()
    refreshAiUsage()
  }, [isDeveloper, session.id])
  useEffect(() => {
    if (loading || initialScrollDoneRef.current || messages.length === 0) return
    bottomRef.current?.scrollIntoView({ behavior: "auto" })
    initialScrollDoneRef.current = true
  }, [loading, messages.length])

  useEffect(() => {
    const anchorId = historyAnchorIdRef.current
    if (anchorId == null || loadingOlder) return
    document.querySelector(`[data-pocket-message-id="${anchorId}"]`)
      ?.scrollIntoView({ block: "start", behavior: "auto" })
    historyAnchorIdRef.current = null
  }, [loadingOlder, messages.length])

  useEffect(() => {
    const viewport = scrollAreaRef.current?.querySelector('[data-slot="scroll-area-viewport"]')
    const sentinel = historySentinelRef.current
    if (!viewport || !sentinel || !hasOlderMessages || loadingOlder || loading) return

    const observer = new IntersectionObserver(
      entries => {
        if (entries[0]?.isIntersecting) loadOlderMessages()
      },
      { root: viewport, rootMargin: "24px 0px 0px 0px", threshold: 0 }
    )

    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [hasOlderMessages, loadingOlder, loading, messages[0]?.id])

  function followTyping() {
    const viewport=scrollAreaRef.current?.querySelector('[data-slot="scroll-area-viewport"]')
    if(viewport && viewport.scrollHeight-viewport.scrollTop-viewport.clientHeight<120)
      bottomRef.current?.scrollIntoView({block:"end",behavior:"auto"})
  }

  async function selectPlayerProfile(profile) {
    if (changingPlayerProfile || sending) return
    setChangingPlayerProfile(true)
    setError("")
    try {
      const response = await fetch(`/api/worlds/pockets/${session.id}/player-profile`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ playerProfileId: profile?.id ?? null }),
      })
      if (!response.ok) throw new Error("플레이어 프로필을 변경하지 못했어요.")
      setSelectedPlayerProfile(profile ?? null)
      setPickerMode(false)
    } catch (e) {
      setError(e.message)
    } finally {
      setChangingPlayerProfile(false)
    }
  }

  function refreshPlayerState() {
    setPlayerStateRefreshKey(current => current + 1)
  }

  async function sendMessage() {
    const message = input.trim()
    if (sending || loading) return
    if (!message) {
      await continueScene()
      return
    }
    setSending(true)
    setError("")
    setInput("")
    const pending = { id: `pending-${Date.now()}`, role: "USER", content: message }
    setMessages(current => [...current, pending])
    try {
      if (!await progress.resume()) throw new Error("먼저 부재 활동을 확인해 주세요.")
      const response = await fetch(`${base}/chat`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ message }),
      })
      if (!response.ok) throw new Error("대화를 이어가지 못했어요.")
      await refresh({animateReply:true})
      refreshPlayerState()
      await refreshMemoryInspector()
      await refreshAiUsage()
    } catch (e) {
      setMessages(current => current.filter(item => item.id !== pending.id))
      setInput(message)
      setError(e.message)
    } finally {
      setSending(false)
    }
  }


  async function editMessage(item) {
    const content = editText.trim()
    if (!content || sending) return
    setSending(true)
    setError("")
    try {
      if (!await progress.resume()) throw new Error("먼저 부재 활동을 확인해 주세요.")
      const response = await fetch(`${base}/messages/${item.id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ content }),
      })
      if (!response.ok) throw new Error("메시지를 수정하지 못했어요.")
      setEditing(null)
      setEditText("")
      await refresh()
    } catch (e) { setError(e.message) }
    finally { setSending(false) }
  }

  async function reroll(item) {
    if (sending) return
    setSending(true)
    setError("")
    try {
      if (!await progress.resume()) throw new Error("먼저 부재 활동을 확인해 주세요.")
      const response = await fetch(`${base}/messages/${item.id}/reroll`, { method: "POST", credentials: "include" })
      if (response.status === 409) throw new Error("이 응답은 이미 이어진 과거 장면이라 리롤할 수 없어요.")
      if (!response.ok) throw new Error("리롤하지 못했어요.")
      await refresh({animateReply:true})
      refreshPlayerState()
      await loadVariants(item.id)
    } catch (e) { setError(e.message) }
    finally { setSending(false) }
  }

  async function loadVariants(messageId) {
    const response = await fetch(`${base}/messages/${messageId}/variants`, { credentials: "include" })
    if (!response.ok) return
    const variants = await response.json()
    setVariantsByMessage(current => ({ ...current, [messageId]: variants }))
  }

  async function selectVariant(item, variantNo) {
    if (sending) return
    setSending(true)
    setError("")
    try {
      if (!await progress.resume()) throw new Error("먼저 부재 활동을 확인해 주세요.")
      const response = await fetch(`${base}/messages/${item.id}/variants/${variantNo}`, { method: "PUT", credentials: "include" })
      if (response.status === 409) throw new Error("이 응답은 이미 이어진 과거 장면이라 다른 버전으로 바꿀 수 없어요.")
      if (!response.ok) throw new Error("다른 버전을 선택하지 못했어요.")
      await refresh()
      refreshPlayerState()
      await loadVariants(item.id)
    } catch (e) { setError(e.message) }
    finally { setSending(false) }
  }

  async function deleteFrom(item) {
    if (sending || !window.confirm("이 메시지부터 이후의 대화를 삭제할까요?")) return
    setSending(true)
    setError("")
    try {
      if (!await progress.resume()) throw new Error("먼저 부재 활동을 확인해 주세요.")
      const response = await fetch(`${base}/messages/${item.id}`, { method: "DELETE", credentials: "include" })
      if (!response.ok) throw new Error("메시지를 삭제하지 못했어요.")
      await progress.reload()
      await refresh()
      refreshPlayerState()
      await refreshMemoryInspector()
      await refreshAiUsage()
    } catch (e) { setError(e.message) }
    finally { setSending(false) }
  }

  async function continueScene() {
    if (sending || loading) return
    setSending(true)
    setError("")
    try {
      if (!await progress.resume()) throw new Error("먼저 부재 활동을 확인해 주세요.")
      const response = await fetch(`${base}/continue`, {
        method: "POST",
        credentials: "include",
      })
      if (!response.ok) throw new Error("장면을 이어가지 못했어요.")
      await refresh({animateReply:true})
      refreshPlayerState()
      await refreshMemoryInspector()
      await refreshAiUsage()
    } catch (e) {
      setError(e.message)
    } finally {
      setSending(false)
    }
  }

  async function setDevSystemValue(system) {
    const raw = devSystemValues[system.systemId] ?? system.currentValue
    const value = Number(raw)
    if (!Number.isInteger(value) || value < system.minValue || value > system.maxValue) {
      setError(`${system.name} 값은 ${system.minValue}~${system.maxValue} 사이의 정수여야 해요.`)
      return
    }
    setError("")
    const response = await fetch(`${base}/systems/${system.systemId}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ value }),
    })
    if (!response.ok) {
      setError("개발자 상태 값을 저장하지 못했어요.")
      return
    }
    await refresh()
  }

  function handleKeyDown(event) {
    if (enterSendsMessage(event,chatInputEnvironment())) {
      event.preventDefault()
      sendMessage()
    }
  }

  if (pickerMode) {
    return <main className="min-h-svh bg-background text-foreground">
      <p className="mx-auto max-w-2xl px-4 pt-6 text-sm text-muted-foreground">{room.title} · 유저 프로필 선택</p>
      {error && <p role="alert" className="mx-auto max-w-2xl px-4 py-2 text-destructive">{error}</p>}
      <PlayerProfilePicker
        current={selectedPlayerProfile}
        busy={changingPlayerProfile}
        onSelect={selectPlayerProfile}
        onSaved={profile => {
          if (selectedPlayerProfile?.id === profile.id) setSelectedPlayerProfile(profile)
        }}
        onCancel={() => setPickerMode(false)}
      />
    </main>
  }

  return <main className="pocket-page h-svh bg-background text-foreground">
    <div className="pocket-layout">
      <section className="pocket-main flex h-full w-full max-w-4xl flex-col border-x bg-card">
      <header className="flex min-h-16 shrink-0 flex-wrap items-center gap-3 border-b px-4 py-2 sm:px-6">
        <Button type="button" variant="ghost" size="icon" onClick={onBack} disabled={sending} aria-label="월드로 돌아가기">
          <ArrowLeft className="size-4" />
        </Button>
        <div className="min-w-0">
          <h1 className="truncate font-medium leading-none">{room.title}</h1>
          <p className="mt-1 text-xs text-muted-foreground">{world.name} · POCKET</p>
        </div>
        <Button type="button" variant="ghost" size="sm" disabled={sending}
          onClick={() => { setIssueNotice(""); setIssueManagerOpen(true) }}>문제 기록 관리</Button>
        {issueNotice && <p role="status" className="text-xs text-muted-foreground">{issueNotice}</p>}
        <Button
          type="button"
          variant="ghost"
          className="ml-auto shrink-0"
          disabled={loading || sending || changingPlayerProfile}
          onClick={() => { setError(""); setPickerMode(true) }}
        >
          {selectedPlayerProfile?.avatar
            ? <img src={selectedPlayerProfile.avatar} alt="" className="size-7 rounded-full object-cover" />
            : <span className="grid size-7 place-items-center rounded-full bg-accent text-xs text-accent-foreground">{selectedPlayerProfile?.name?.slice(0, 1) ?? "나"}</span>}
          {selectedPlayerProfile?.name ?? "프로필 선택"}
        </Button>
        <Button type="button" variant="ghost" size="icon" className="pocket-mobile-menu-button"
          aria-label="포켓 메뉴 열기" aria-haspopup="dialog" aria-expanded={mobileToolsOpen}
          onClick={()=>setMobileToolsOpen(true)}><Menu className="size-5" /></Button>
      </header>

      <section className={progress.status?.lastReport?.activities?.length ? "shrink-0 border-b bg-muted/10 px-4 py-3 sm:px-6" : "contents"}>
        <PocketActivityScene
          isDeveloper={isDeveloper}
          sessionId={session.id}
          allowGenerate={!messages.some(m => m.absenceReportId == null && m.id > (messages.find(m => m.absenceReportId === progress.status?.lastReport?.id)?.id ?? -1))}
          report={progress.status?.lastReport}
          freshReportId={progress.freshReportId}
          onDiscard={progress.discard}
          progressError={progress.error}
          characters={worldCharacters}
          sceneModels={sceneModels}
          simulatedAt={sceneRuntimeState.simulatedAt}
          location={sceneRuntimeState.location}
          disabled={sending || loading}
        />
      </section>

      {error && <p role="alert" className="shrink-0 border-b px-4 py-2 text-sm text-destructive">{error}</p>}

      <dialog ref={mobileToolsRef} className="pocket-mobile-tools" aria-label="포켓 메뉴"
        onCancel={event=>{event.preventDefault();setMobileToolsOpen(false)}}>
        <div className="flex items-center justify-between border-b px-4 py-3">
          <h2 className="text-sm font-medium">포켓 메뉴</h2>
          <Button type="button" variant="ghost" size="icon" aria-label="포켓 메뉴 닫기"
            onClick={()=>setMobileToolsOpen(false)}><X className="size-4" /></Button>
        </div>
        <div className="border-b px-4 py-3">
          <Button type="button" variant="outline" className="w-full" disabled={exportingTestData || sending}
            onClick={exportTestData}>{exportingTestData ? "기록 수집 중..." : "장기 테스트 기록 내보내기 (.json)"}</Button>
          <p className="mt-2 text-xs text-muted-foreground">전체 대화·리롤 버전과 현재 AI 사용량·장면·메모리 진단을 저장합니다.</p>
        </div>
        <div className="shrink-0 border-b px-4 py-2 text-xs text-muted-foreground sm:px-6">
          <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
            <span>현재 장면 · {cast.length ? cast.map(item => item.characterName ?? item.name).join(" · ") : "등장인물 없음"}</span>
            {systems.map(system => <span key={system.id}>{system.name} <strong className="text-foreground">{system.currentValue}</strong></span>)}
          </div>
        </div>

      <div>
        {isDeveloper && <PocketPlayerStatePanel
          sessionId={session.id}
          onError={setError}
          refreshKey={playerStateRefreshKey}
          onStateChange={state => setSceneRuntimeState({
            simulatedAt: state.simulatedAt,
            location: state.location,
            inventory: state.inventory ?? [],
            conditions: state.conditions ?? [],
          })} />}
        <PocketProgressPanel modelsReady={sceneModels.ready} modelError={sceneModels.blockedReason} isDeveloper={isDeveloper} progress={progress} characters={worldCharacters} disabled={messageSending} />
        <PocketSettingsPanel sessionId={session.id} location={sceneRuntimeState.location} simulatedAt={sceneRuntimeState.simulatedAt} onError={setError} />
      </div>

      <div>
      {isDeveloper && <section className="shrink-0 border-b bg-muted/20">
        <button type="button" onClick={() => setDevOpen(open => !open)}
          className="flex w-full items-center gap-2 px-4 py-2 text-left text-xs font-medium text-muted-foreground sm:px-6">
          <Wrench className="size-3.5" />
          개발자 도구
          <ChevronDown className={`ml-auto size-3.5 transition-transform ${devOpen ? "rotate-180" : ""}`} />
        </button>
        {devOpen && <div className="border-t px-4 py-3 sm:px-6">
          <div className="mb-3 flex flex-wrap gap-x-5 gap-y-1 text-xs text-muted-foreground">
            <span>SESSION #{session.id}</span>
            <span>시뮬레이션 시각 {session.simulatedAt ?? "-"}</span>
          </div>
          <div className="rounded-lg border bg-background p-3">
            <p className="mb-2 text-xs font-semibold">AI 사용량 · 최근 OpenAI 턴</p>
            {aiUsage
              ? <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
                  <PromptTokenUsage usage={aiUsage} />
                  <AiCallUsage calls={aiCalls} diagnostics={promptDiagnostics} />
                    <span>{aiUsage.model}</span>
                  <span>입력 <strong className="text-foreground">{aiUsage.inputTokens?.toLocaleString()}</strong></span>
                  <span>일반 입력 <strong className="text-foreground">{aiUsage.ordinaryInputTokens == null ? "미제공" : aiUsage.ordinaryInputTokens.toLocaleString()}</strong></span>
                  <span>캐시 읽기 <strong className="text-foreground">{aiUsage.cachedInputTokens?.toLocaleString()}</strong></span>
                  <span>캐시 쓰기 <strong className="text-foreground">{aiUsage.cacheWriteTokens == null ? "미제공" : aiUsage.cacheWriteTokens.toLocaleString()}</strong></span>
                  <span>출력 <strong className="text-foreground">{aiUsage.outputTokens?.toLocaleString()}</strong></span>
                  <span>총 <strong className="text-foreground">{aiUsage.totalTokens?.toLocaleString()}</strong></span>
                  <span title="표준 텍스트 토큰 기준 예상액 · 캐시 쓰기 포함 · 추가 도구·세금 제외">예상 비용 <strong className="text-foreground">{aiUsage.estimatedCostUsd == null ? "산정 불가" : `$${Number(aiUsage.estimatedCostUsd).toFixed(6)}`}</strong></span>
                </div>
              : <p className="text-xs text-muted-foreground">아직 기록된 OpenAI 토큰 사용량이 없습니다.</p>}
          </div>
          <div className="mt-3 rounded-lg border bg-background p-3">
            <p className="mb-2 text-xs font-semibold">Pocket 상태 치트</p>
            {systems.length === 0
              ? <p className="text-xs text-muted-foreground">등록된 World System이 없습니다.</p>
              : <div className="grid gap-2 sm:grid-cols-2">
                {systems.map(system => <div key={system.systemId} className="flex items-center gap-2 rounded-md border p-2">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-xs font-medium">{system.name}</p>
                    <p className="text-[11px] text-muted-foreground">{system.minValue} ~ {system.maxValue} · 현재 {system.currentValue}</p>
                  </div>
                  <input type="number" min={system.minValue} max={system.maxValue}
                    value={devSystemValues[system.systemId] ?? system.currentValue}
                    onChange={event => setDevSystemValues(current => ({ ...current, [system.systemId]: event.target.value }))}
                    className="h-8 w-20 rounded-md border bg-background px-2 text-sm" />
                  <Button type="button" size="sm" variant="outline" className="h-8"
                    onClick={() => setDevSystemValue(system)}>저장</Button>
                </div>)}
              </div>}
          </div>
          <div className="mt-3 rounded-lg border bg-background p-3">
            <p className="mb-2 text-xs font-semibold">이번 턴 Memory Diff</p>
            {!memoryDiff || !memoryDiff.turnAt
              ? <p className="text-xs text-muted-foreground">아직 기록된 Memory 변화가 없습니다.</p>
              : <>
                <div className="mb-2 flex flex-wrap gap-3 text-xs">
                  <span>+ ADDED <strong>{memoryDiff.added}</strong></span>
                  <span>~ UPDATED <strong>{memoryDiff.updated}</strong></span>
                  <span>⇣ COMPRESSED <strong>{memoryDiff.compressed}</strong></span>
                  <span>- DELETED <strong>{memoryDiff.deleted}</strong></span>
                </div>
                <div className="space-y-1 text-[11px] text-muted-foreground">
                  {memoryDiff.items.map(item =>
                    <p key={`${item.type}-${item.memoryId}`}>
                      <span className="font-medium text-foreground">+ {item.characterName} · ID {item.memoryId}</span>
                      {" · "}{item.summary}
                    </p>
                  )}
                </div>
              </>}
          </div>
          <div className="mt-3 rounded-lg border p-3">
            <p className="mb-3 text-xs font-semibold">Memory Inspector · 캐릭터별 최신 5개</p>
            {memoryInspector.length === 0
              ? <p className="text-xs text-muted-foreground">이 Pocket에 저장된 캐릭터 기억이 아직 없습니다.</p>
              : <div className="space-y-4">
                {memoryInspector.map(group => <div key={group.characterId}>
                  <div className="mb-1.5 flex items-center gap-2 text-xs">
                    <strong>{group.characterName}</strong>
                    <span className="text-muted-foreground">{group.active ? "ACTIVE" : "INACTIVE"}</span>
                  </div>
                  {group.memories.length === 0
                    ? <p className="rounded-md border border-dashed p-2 text-xs text-muted-foreground">저장된 기억 없음</p>
                    : <div className="overflow-x-auto rounded-md border">
                      <table className="w-full min-w-[680px] text-left text-[11px]">
                        <thead className="bg-muted/40 text-muted-foreground">
                          <tr><th className="px-2 py-1.5">ID</th><th className="px-2 py-1.5">단계</th><th className="px-2 py-1.5">중요도</th><th className="px-2 py-1.5">기억</th><th className="px-2 py-1.5">근거</th></tr>
                        </thead>
                        <tbody>
                          {group.memories.map(memory => <tr key={memory.id} className="border-t align-top">
                            <td className="whitespace-nowrap px-2 py-2">{memory.id}</td>
                            <td className="whitespace-nowrap px-2 py-2">{memory.detailLevel}</td>
                            <td className="px-2 py-2">{memory.importance}</td>
                            <td className="max-w-72 px-2 py-2">{memory.summary}</td>
                            <td className="max-w-64 px-2 py-2 text-muted-foreground">{memory.evidenceQuote ?? "-"}</td>
                          </tr>)}
                        </tbody>
                      </table>
                    </div>}
                </div>)}
              </div>}
          </div>
        </div>}
      </section>}

      </div>
      </dialog>

      <ScrollArea ref={scrollAreaRef} className="min-h-0 flex-1">
        <div className="mx-auto flex w-full max-w-3xl flex-col p-4 pb-32 sm:p-6 sm:pb-36">
          {loading && <p className="py-10 text-center text-sm text-muted-foreground">포켓을 불러오는 중...</p>}

          {!loading && hasOlderMessages && <div ref={historySentinelRef} className="flex min-h-12 items-center justify-center border-b border-border/70 py-3 text-xs text-muted-foreground">
            {loadingOlder
              ? <span className="flex items-center gap-2"><LoaderCircle className="size-4 animate-spin" />이전 대화를 불러오는 중...</span>
              : <span>위로 스크롤하면 이전 대화를 불러옵니다.</span>}
          </div>}

          {!loading && !hasOlderMessages && <article className="border-b border-border/70 py-7 sm:py-9">
            <p className="mb-4 text-xs font-semibold tracking-[.12em] text-muted-foreground">INTRO · {session.introTitle}</p>
            <RpText text={session.introContent} />
            {initialScene?.reference && <p className="mt-4 text-xs text-muted-foreground">초기 상태 참고값 · 현재 인트로 설정 기준</p>}
            <SceneStateMessage state={initialScene ?? { simulatedAt: "", location: "", inventory: [], conditions: [] }} />
          </article>}

          {!loading && messages.map((item, index) => {
            const isCharacter = item.role === "CHARACTER"
            const isAbsence = item.absenceReportId != null
            const isLatest = index === messages.length - 1
            const variants = variantsByMessage[item.id] ?? []
            const selectedIndex = variants.findIndex(variant => variant.selected)
            const messageSceneState = isCharacter ? snapshotSceneState(variants.find(variant => variant.selected)) : null
            return <article key={item.id} data-pocket-message-id={item.id} className="group border-b border-border/70 py-7 last:border-b-0 sm:py-9">
              {editing === item.id ? <div className="space-y-2">
                <Textarea autoFocus value={editText} onChange={e => setEditText(e.target.value)} className="min-h-32 resize-y text-base leading-7" />
                <div className="flex justify-end gap-2"><Button size="sm" variant="ghost" onClick={() => setEditing(null)}><X className="mr-1 size-3.5"/>취소</Button><Button size="sm" onClick={() => editMessage(item)}>저장</Button></div>
              </div> : isAbsence ? <PocketAbsenceLog sessionId={session.id} reportId={item.absenceReportId} allowGenerate={!messages.slice(index+1).some(m => m.absenceReportId == null)} isDeveloper={isDeveloper} fallback={item.content ?? item.message} disabled={sending} /> : <TypedPocketReply key={`${item.id}:${item.selectedVariantNo ?? 1}:${item.content ?? item.message}`}
                text={item.content ?? item.message}
                animate={isCharacter && !isAbsence && typingTarget===`${item.id}:${item.selectedVariantNo ?? 1}`}
                onProgress={followTyping}>
                {messageSceneState && <SceneStateMessage state={messageSceneState} />}
              </TypedPocketReply>}
              {editing !== item.id && <div className="mt-4 flex min-h-7 flex-wrap items-center gap-1 text-xs text-muted-foreground opacity-40 transition-opacity group-hover:opacity-100">
                {!isAbsence && <Button size="sm" variant="ghost" className="h-7 px-2" onClick={() => { setEditing(item.id); setEditText(item.content ?? item.message ?? "") }} disabled={sending}><Pencil className="mr-1 size-3"/>수정</Button>}
                {isCharacter && !isAbsence && isLatest && <Button size="sm" variant="ghost" className="h-7 px-2" onClick={() => reroll(item)} disabled={sending}><RefreshCw className="mr-1 size-3"/>리롤</Button>}
                {isCharacter && !isAbsence && <Button size="sm" variant="ghost" className="h-7 px-2" onClick={() => loadVariants(item.id)} disabled={sending}>버전 {item.selectedVariantNo ?? 1}</Button>}
                {isCharacter && <Button size="sm" variant="ghost" className="h-7 px-2" disabled={sending}
                  onClick={() => { setIssueNotice(""); setIssueTarget({ ...item }) }}>문제 기록</Button>}
                {variants.length > 1 && <div className="flex items-center gap-1">
                  <Button size="icon" variant="ghost" className="size-7" disabled={sending || selectedIndex <= 0 || !isLatest} onClick={() => selectVariant(item, variants[selectedIndex - 1].variantNo)}><ChevronLeft className="size-3"/></Button>
                  <span>{selectedIndex + 1} / {variants.length}</span>
                  <Button size="icon" variant="ghost" className="size-7" disabled={sending || selectedIndex < 0 || selectedIndex >= variants.length - 1 || !isLatest} onClick={() => selectVariant(item, variants[selectedIndex + 1].variantNo)}><ChevronRight className="size-3"/></Button>
                </div>}
                <Button size="sm" variant="ghost" className="ml-auto h-7 px-2 text-destructive hover:text-destructive" onClick={() => deleteFrom(item)} disabled={sending}><Trash2 className="mr-1 size-3"/>이후 삭제</Button>
              </div>}
            </article>
          })}

          {sending && <div className="border-b border-border/70 py-7 sm:py-9">
            <p className="animate-pulse text-sm italic text-muted-foreground">{progress.busy ? "부재 활동을 확인하는 중…" : "장면을 이어가는 중..."}</p>
          </div>}
          <div ref={bottomRef} />
        </div>
      </ScrollArea>

      <div className="shrink-0 border-t bg-card p-3 sm:p-4">
        <div className="mx-auto flex max-w-3xl items-end gap-2">
          <Textarea value={input} onChange={e => setInput(e.target.value)} onKeyDown={handleKeyDown}
            placeholder="행동이나 대사를 입력해 주세요." disabled={loading || sending} rows={1}
            className="max-h-36 min-h-11 resize-none text-base" />
          <Button type="button" size="icon" className="size-11 shrink-0" disabled={loading || sending}
            onClick={sendMessage} aria-label="메시지 보내기"><Send className="size-4" /></Button>
        </div>
        <p className="mx-auto mt-2 max-w-3xl text-center text-xs text-muted-foreground">PC: Enter 전송 · Shift + Enter 줄바꿈 / 모바일: Enter 줄바꿈 · 버튼으로 전송</p>
      </div>
      </section>
      <aside className="pocket-desktop-tools">
        <div className="shrink-0 border-b px-4 py-2 text-xs text-muted-foreground sm:px-6">
          <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
            <span>현재 장면 · {cast.length ? cast.map(item => item.characterName ?? item.name).join(" · ") : "등장인물 없음"}</span>
            {systems.map(system => <span key={system.id}>{system.name} <strong className="text-foreground">{system.currentValue}</strong></span>)}
          </div>
        </div>

      <div>
        {isDeveloper && <PocketPlayerStatePanel
          sessionId={session.id}
          onError={setError}
          refreshKey={playerStateRefreshKey}
          onStateChange={state => setSceneRuntimeState({
            simulatedAt: state.simulatedAt,
            location: state.location,
            inventory: state.inventory ?? [],
            conditions: state.conditions ?? [],
          })} />}
        <PocketProgressPanel modelsReady={sceneModels.ready} modelError={sceneModels.blockedReason} isDeveloper={isDeveloper} progress={progress} characters={worldCharacters} disabled={messageSending} />
        <PocketSettingsPanel sessionId={session.id} location={sceneRuntimeState.location} simulatedAt={sceneRuntimeState.simulatedAt} onError={setError} />
      </div>

      <div>
      {isDeveloper && <section className="shrink-0 border-b bg-muted/20">
        <button type="button" onClick={() => setDevOpen(open => !open)}
          className="flex w-full items-center gap-2 px-4 py-2 text-left text-xs font-medium text-muted-foreground sm:px-6">
          <Wrench className="size-3.5" />
          개발자 도구
          <ChevronDown className={`ml-auto size-3.5 transition-transform ${devOpen ? "rotate-180" : ""}`} />
        </button>
        {devOpen && <div className="border-t px-4 py-3 sm:px-6">
          <div className="mb-3 flex flex-wrap gap-x-5 gap-y-1 text-xs text-muted-foreground">
            <span>SESSION #{session.id}</span>
            <span>시뮬레이션 시각 {session.simulatedAt ?? "-"}</span>
          </div>
          <div className="rounded-lg border bg-background p-3">
            <p className="mb-2 text-xs font-semibold">AI 사용량 · 최근 OpenAI 턴</p>
            {aiUsage
              ? <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
                  <PromptTokenUsage usage={aiUsage} />
                  <AiCallUsage calls={aiCalls} diagnostics={promptDiagnostics} />
                    <span>{aiUsage.model}</span>
                  <span>입력 <strong className="text-foreground">{aiUsage.inputTokens?.toLocaleString()}</strong></span>
                  <span>일반 입력 <strong className="text-foreground">{aiUsage.ordinaryInputTokens == null ? "미제공" : aiUsage.ordinaryInputTokens.toLocaleString()}</strong></span>
                  <span>캐시 읽기 <strong className="text-foreground">{aiUsage.cachedInputTokens?.toLocaleString()}</strong></span>
                  <span>캐시 쓰기 <strong className="text-foreground">{aiUsage.cacheWriteTokens == null ? "미제공" : aiUsage.cacheWriteTokens.toLocaleString()}</strong></span>
                  <span>출력 <strong className="text-foreground">{aiUsage.outputTokens?.toLocaleString()}</strong></span>
                  <span>총 <strong className="text-foreground">{aiUsage.totalTokens?.toLocaleString()}</strong></span>
                  <span title="표준 텍스트 토큰 기준 예상액 · 캐시 쓰기 포함 · 추가 도구·세금 제외">예상 비용 <strong className="text-foreground">{aiUsage.estimatedCostUsd == null ? "산정 불가" : `$${Number(aiUsage.estimatedCostUsd).toFixed(6)}`}</strong></span>
                </div>
              : <p className="text-xs text-muted-foreground">아직 기록된 OpenAI 토큰 사용량이 없습니다.</p>}
          </div>
          <div className="mt-3 rounded-lg border bg-background p-3">
            <p className="mb-2 text-xs font-semibold">Pocket 상태 치트</p>
            {systems.length === 0
              ? <p className="text-xs text-muted-foreground">등록된 World System이 없습니다.</p>
              : <div className="grid gap-2 sm:grid-cols-2">
                {systems.map(system => <div key={system.systemId} className="flex items-center gap-2 rounded-md border p-2">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-xs font-medium">{system.name}</p>
                    <p className="text-[11px] text-muted-foreground">{system.minValue} ~ {system.maxValue} · 현재 {system.currentValue}</p>
                  </div>
                  <input type="number" min={system.minValue} max={system.maxValue}
                    value={devSystemValues[system.systemId] ?? system.currentValue}
                    onChange={event => setDevSystemValues(current => ({ ...current, [system.systemId]: event.target.value }))}
                    className="h-8 w-20 rounded-md border bg-background px-2 text-sm" />
                  <Button type="button" size="sm" variant="outline" className="h-8"
                    onClick={() => setDevSystemValue(system)}>저장</Button>
                </div>)}
              </div>}
          </div>
          <div className="mt-3 rounded-lg border bg-background p-3">
            <p className="mb-2 text-xs font-semibold">이번 턴 Memory Diff</p>
            {!memoryDiff || !memoryDiff.turnAt
              ? <p className="text-xs text-muted-foreground">아직 기록된 Memory 변화가 없습니다.</p>
              : <>
                <div className="mb-2 flex flex-wrap gap-3 text-xs">
                  <span>+ ADDED <strong>{memoryDiff.added}</strong></span>
                  <span>~ UPDATED <strong>{memoryDiff.updated}</strong></span>
                  <span>⇣ COMPRESSED <strong>{memoryDiff.compressed}</strong></span>
                  <span>- DELETED <strong>{memoryDiff.deleted}</strong></span>
                </div>
                <div className="space-y-1 text-[11px] text-muted-foreground">
                  {memoryDiff.items.map(item =>
                    <p key={`${item.type}-${item.memoryId}`}>
                      <span className="font-medium text-foreground">+ {item.characterName} · ID {item.memoryId}</span>
                      {" · "}{item.summary}
                    </p>
                  )}
                </div>
              </>}
          </div>
          <div className="mt-3 rounded-lg border p-3">
            <p className="mb-3 text-xs font-semibold">Memory Inspector · 캐릭터별 최신 5개</p>
            {memoryInspector.length === 0
              ? <p className="text-xs text-muted-foreground">이 Pocket에 저장된 캐릭터 기억이 아직 없습니다.</p>
              : <div className="space-y-4">
                {memoryInspector.map(group => <div key={group.characterId}>
                  <div className="mb-1.5 flex items-center gap-2 text-xs">
                    <strong>{group.characterName}</strong>
                    <span className="text-muted-foreground">{group.active ? "ACTIVE" : "INACTIVE"}</span>
                  </div>
                  {group.memories.length === 0
                    ? <p className="rounded-md border border-dashed p-2 text-xs text-muted-foreground">저장된 기억 없음</p>
                    : <div className="overflow-x-auto rounded-md border">
                      <table className="w-full min-w-[680px] text-left text-[11px]">
                        <thead className="bg-muted/40 text-muted-foreground">
                          <tr><th className="px-2 py-1.5">ID</th><th className="px-2 py-1.5">단계</th><th className="px-2 py-1.5">중요도</th><th className="px-2 py-1.5">기억</th><th className="px-2 py-1.5">근거</th></tr>
                        </thead>
                        <tbody>
                          {group.memories.map(memory => <tr key={memory.id} className="border-t align-top">
                            <td className="whitespace-nowrap px-2 py-2">{memory.id}</td>
                            <td className="whitespace-nowrap px-2 py-2">{memory.detailLevel}</td>
                            <td className="px-2 py-2">{memory.importance}</td>
                            <td className="max-w-72 px-2 py-2">{memory.summary}</td>
                            <td className="max-w-64 px-2 py-2 text-muted-foreground">{memory.evidenceQuote ?? "-"}</td>
                          </tr>)}
                        </tbody>
                      </table>
                    </div>}
                </div>)}
              </div>}
          </div>
        </div>}
      </section>}

      </div>
      </aside>
    </div>
    <DialogueIssuePanel sessionId={session.id} target={issueTarget} managerOpen={issueManagerOpen}
      onClose={result => { setIssueTarget(null); setIssueManagerOpen(false); if (result === "saved") setIssueNotice("문제 기록을 저장했어요.") }} />
  </main>
}
