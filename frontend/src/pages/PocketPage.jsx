import { useEffect, useRef, useState } from "react"
import { ArrowLeft, ChevronDown, ChevronLeft, ChevronRight, Pencil, RefreshCw, Send, Trash2, Wrench, X } from "lucide-react"

import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Textarea } from "@/components/ui/textarea"

function RpText({ text }) {
  const parts = (text ?? "").split(/(\*[^*]+\*)/g)
  return <div className="whitespace-pre-wrap text-[15px] leading-7 text-foreground sm:text-base sm:leading-8">
    {parts.map((part, index) => {
      const action = part.startsWith("*") && part.endsWith("*") && part.length > 2
      return action
        ? <span key={index} className="italic text-muted-foreground">{part.slice(1, -1)}</span>
        : <span key={index}>{part}</span>
    })}
  </div>
}

export default function PocketPage({ pocket, onBack }) {
  const { world, room, session, playerProfile } = pocket
  const base = `/api/worlds/${world.id}/rooms/${room.id}/sessions/${session.id}`
  const [messages, setMessages] = useState([])
  const [cast, setCast] = useState([])
  const [systems, setSystems] = useState([])
  const [input, setInput] = useState("")
  const [sending, setSending] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")
  const [editing, setEditing] = useState(null)
  const [editText, setEditText] = useState("")
  const [variantsByMessage, setVariantsByMessage] = useState({})
  const [isDeveloper, setIsDeveloper] = useState(false)
  const [devOpen, setDevOpen] = useState(false)
  const [devSystemValues, setDevSystemValues] = useState({})
  const [memoryInspector, setMemoryInspector] = useState([])
  const [memoryDiff, setMemoryDiff] = useState(null)
  const [hasOlderMessages, setHasOlderMessages] = useState(false)
  const [loadingOlder, setLoadingOlder] = useState(false)
  const bottomRef = useRef(null)
  const initialScrollDoneRef = useRef(false)

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

  async function loadMessagePage(before = null) {
    const url = before == null
      ? `${base}/messages`
      : `${base}/messages?before=${before}`
    const response = await fetch(url, { credentials: "include" })
    if (!response.ok) throw new Error("포켓의 대화를 불러오지 못했어요.")
    return response.json()
  }

  async function refresh() {
    try {
      const [loadedMessages, castResponse, systemResponse] = await Promise.all([
        loadMessagePage(),
        fetch(`${base}/cast`, { credentials: "include" }),
        fetch(`${base}/systems`, { credentials: "include" }),
      ])
      setMessages(loadedMessages)
      setHasOlderMessages(loadedMessages.length === 15)
      if (castResponse.ok) setCast(await castResponse.json())
      if (systemResponse.ok) setSystems(await systemResponse.json())
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
      const page = await loadMessagePage(messages[0].id)
      setMessages(current => [...page, ...current])
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
  useEffect(() => {
    if (!isDeveloper) {
      setMemoryInspector([])
      setMemoryDiff(null)
      return
    }
    refreshMemoryInspector()
  }, [isDeveloper, session.id])
  useEffect(() => {
    if (loading || initialScrollDoneRef.current || messages.length === 0) return
    bottomRef.current?.scrollIntoView({ behavior: "auto" })
    initialScrollDoneRef.current = true
  }, [loading, messages.length])

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
      const response = await fetch(`${base}/chat`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ message }),
      })
      if (!response.ok) throw new Error("대화를 이어가지 못했어요.")
      await refresh()
      await refreshMemoryInspector()
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
      const response = await fetch(`${base}/messages/${item.id}/reroll`, { method: "POST", credentials: "include" })
      if (response.status === 409) throw new Error("이 응답은 이미 이어진 과거 장면이라 리롤할 수 없어요.")
      if (!response.ok) throw new Error("리롤하지 못했어요.")
      await refresh()
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
      const response = await fetch(`${base}/messages/${item.id}/variants/${variantNo}`, { method: "PUT", credentials: "include" })
      if (response.status === 409) throw new Error("이 응답은 이미 이어진 과거 장면이라 다른 버전으로 바꿀 수 없어요.")
      if (!response.ok) throw new Error("다른 버전을 선택하지 못했어요.")
      await refresh()
      await loadVariants(item.id)
    } catch (e) { setError(e.message) }
    finally { setSending(false) }
  }

  async function deleteFrom(item) {
    if (sending || !window.confirm("이 메시지부터 이후의 대화를 삭제할까요?")) return
    setSending(true)
    setError("")
    try {
      const response = await fetch(`${base}/messages/${item.id}`, { method: "DELETE", credentials: "include" })
      if (!response.ok) throw new Error("메시지를 삭제하지 못했어요.")
      await refresh()
      await refreshMemoryInspector()
    } catch (e) { setError(e.message) }
    finally { setSending(false) }
  }

  async function continueScene() {
    if (sending || loading) return
    setSending(true)
    setError("")
    try {
      const response = await fetch(`${base}/continue`, {
        method: "POST",
        credentials: "include",
      })
      if (!response.ok) throw new Error("장면을 이어가지 못했어요.")
      await refresh()
      await refreshMemoryInspector()
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
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault()
      sendMessage()
    }
  }

  return <main className="flex h-svh bg-background text-foreground">
    <section className="mx-auto flex h-full w-full max-w-4xl flex-col border-x bg-card">
      <header className="flex min-h-16 shrink-0 flex-wrap items-center gap-3 border-b px-4 py-2 sm:px-6">
        <Button type="button" variant="ghost" size="icon" onClick={onBack} disabled={sending} aria-label="월드로 돌아가기">
          <ArrowLeft className="size-4" />
        </Button>
        <div className="min-w-0">
          <h1 className="truncate font-medium leading-none">{room.title}</h1>
          <p className="mt-1 text-xs text-muted-foreground">{world.name} · POCKET</p>
        </div>
        <div className="ml-auto flex min-w-0 items-center gap-2 text-xs text-muted-foreground">
          <span className="rounded-full bg-accent px-2.5 py-1 text-accent-foreground">{playerProfile?.name ?? "Player"}</span>
        </div>
      </header>

      <div className="shrink-0 border-b px-4 py-2 text-xs text-muted-foreground sm:px-6">
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
          <span>현재 장면 · {cast.length ? cast.map(item => item.name).join(" · ") : "등장인물 없음"}</span>
          {systems.map(system => <span key={system.id}>{system.name} <strong className="text-foreground">{system.currentValue}</strong></span>)}
        </div>
      </div>

      {error && <p role="alert" className="shrink-0 border-b px-4 py-2 text-sm text-destructive">{error}</p>}

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

      <ScrollArea className="min-h-0 flex-1">
        <div className="mx-auto flex w-full max-w-3xl flex-col p-4 pb-32 sm:p-6 sm:pb-36">
          <article className="border-b border-border/70 py-7 sm:py-9">
            <p className="mb-4 text-xs font-semibold tracking-[.12em] text-muted-foreground">INTRO · {session.introTitle}</p>
            <RpText text={session.introContent} />
          </article>

          {loading && <p className="py-10 text-center text-sm text-muted-foreground">포켓을 불러오는 중...</p>}

          {!loading && hasOlderMessages && <div className="flex justify-center border-b border-border/70 py-4">
            <Button type="button" size="sm" variant="ghost" onClick={loadOlderMessages} disabled={loadingOlder}>
              {loadingOlder ? "이전 대화를 불러오는 중..." : "이전 대화 불러오기"}
            </Button>
          </div>}

          {!loading && messages.map((item, index) => {
            const isCharacter = item.role === "CHARACTER"
            const isLatest = index === messages.length - 1
            const variants = variantsByMessage[item.id] ?? []
            const selectedIndex = variants.findIndex(variant => variant.selected)
            return <article key={item.id} className="group border-b border-border/70 py-7 last:border-b-0 sm:py-9">
              {editing === item.id ? <div className="space-y-2">
                <Textarea autoFocus value={editText} onChange={e => setEditText(e.target.value)} className="min-h-32 resize-y text-base leading-7" />
                <div className="flex justify-end gap-2"><Button size="sm" variant="ghost" onClick={() => setEditing(null)}><X className="mr-1 size-3.5"/>취소</Button><Button size="sm" onClick={() => editMessage(item)}>저장</Button></div>
              </div> : <RpText text={item.content ?? item.message} />}
              {editing !== item.id && <div className="mt-4 flex min-h-7 flex-wrap items-center gap-1 text-xs text-muted-foreground opacity-40 transition-opacity group-hover:opacity-100">
                <Button size="sm" variant="ghost" className="h-7 px-2" onClick={() => { setEditing(item.id); setEditText(item.content ?? item.message ?? "") }} disabled={sending}><Pencil className="mr-1 size-3"/>수정</Button>
                {isCharacter && isLatest && <Button size="sm" variant="ghost" className="h-7 px-2" onClick={() => reroll(item)} disabled={sending}><RefreshCw className="mr-1 size-3"/>리롤</Button>}
                {isCharacter && <Button size="sm" variant="ghost" className="h-7 px-2" onClick={() => loadVariants(item.id)} disabled={sending}>버전 {item.selectedVariantNo ?? 1}</Button>}
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
            <p className="animate-pulse text-sm italic text-muted-foreground">장면을 이어가는 중...</p>
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
        <p className="mx-auto mt-2 max-w-3xl text-center text-xs text-muted-foreground">Enter 전송 · 빈 입력으로 전송 시 장면 진행 · Shift + Enter 줄바꿈</p>
      </div>
    </section>
  </main>
}
