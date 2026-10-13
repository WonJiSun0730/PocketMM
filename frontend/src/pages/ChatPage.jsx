import {enterSendsMessage,chatInputEnvironment} from "@/lib/chatInput"
import { useCallback, useEffect, useRef, useState } from "react"
import { ArrowLeft, Send } from "lucide-react"

import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Textarea } from "@/components/ui/textarea"
import PlayerProfilePicker from "@/components/PlayerProfilePicker"
import CustomStatePanel from "@/components/CustomStatePanel"

function formatElapsedMinutes(minutes) {
  if (minutes == null) return "-"
  return `${minutes > 0 ? "+" : ""}${minutes}m`
}

function RpText({ text, isUser }) {
  const parts = text.split(/(\*[^*]+\*)/g)

  return (
    <div
      className={
        isUser
          ? "whitespace-pre-wrap text-[15px] leading-7 text-foreground sm:text-base sm:leading-8"
          : "whitespace-pre-wrap text-[15px] leading-7 text-foreground sm:text-base sm:leading-8"
      }
    >
      {parts.map((part, index) => {
        const isAction =
          part.startsWith("*") &&
          part.endsWith("*") &&
          part.length > 2

        if (isAction) {
          return (
            <span
              key={index}
              className="italic text-muted-foreground"
            >
              {part.slice(1, -1)}
            </span>
          )
        }

        return <span key={index}>{part}</span>
      })}
    </div>
  )
}

export default function ChatPage({
  character,
  world = null,
  onBack,
}) {
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState("")
  const [loading, setLoading] = useState(true)
  const [sending, setSending] = useState(false)
  const [starting, setStarting] = useState(false)
  const [startError, setStartError] = useState("")
  const [session, setSession] = useState(null)
  const [pickerMode, setPickerMode] = useState(null)
  const [progress, setProgress] = useState(null)
  const operationRef = useRef(false)
  const [debugInfo, setDebugInfo] = useState(null)
  const bottomRef = useRef(null)

  const loadMessages = useCallback(async (resume = false) => {
    try {
      if (world) {
        const sessionResponse = await fetch(`/api/worlds/${world.id}/characters/${character.id}/session`, { credentials: "include" })
        if (!sessionResponse.ok) throw new Error("월드 대화방 정보를 불러오지 못했습니다.")
        const room = await sessionResponse.json()
        setSession(room)
        if (!room.playerProfile) setPickerMode("select")
        const response = await fetch(`/api/worlds/${world.id}/characters/${character.id}/messages`, { credentials: "include" })
        if (!response.ok) throw new Error("월드 대화를 불러오지 못했습니다.")
        setMessages(await response.json())
        setProgress(null)
        setStartError("")
        setLoading(false)
        return
      }
      const sessionResponse = await fetch(`/api/chat/${character.id}/session`)
      if (!sessionResponse.ok) throw new Error("대화방 정보를 불러오지 못했습니다.")
      const room = await sessionResponse.json()
      const progressResponse = await fetch(`/api/chat/${character.id}/conversations/${room.conversationId}/${resume ? "resume" : "progress"}`, {
        method: resume ? "POST" : "GET", signal: AbortSignal.timeout(15000),
      })
      if (!progressResponse.ok) throw new Error("자동 진행 상태를 불러오지 못했습니다.")
      setProgress(await progressResponse.json())
      setSession(room)
      // 프로필이 없는 방은 첫 전송 전에 선택 화면으로 안내한다.
      if (!room.playerProfile) setPickerMode("select")
      const response = await fetch(`/api/chat/${character.id}/messages`)

      if (!response.ok) {
        throw new Error(`메시지 조회 실패: ${response.status}`)
      }

      const data = await response.json()
      setMessages(data)
      setStartError("")
    } catch (error) {
      console.error(error)
      setStartError("대화를 불러오지 못했습니다. 새로고침해 주세요.")
    } finally {
      setLoading(false)
    }
  }, [character.id, world])

  const selectedProfileId = session?.playerProfile?.id

  useEffect(() => {
    // StrictMode의 첫 효과 정리는 건너뛰어 초기 입장 요청을 중복 전송하지 않는다.
    let active = true
    queueMicrotask(() => { if (active) loadMessages(true) })
    return () => { active = false }
  }, [loadMessages])

  useEffect(() => {
    if (!session?.conversationId || !selectedProfileId || pickerMode) return
    const roomId = session.conversationId
    let disposed = false
    let lastContact = Date.now()
    let needsResume = false
    let contacting = false
    async function contact(resume) {
      if (disposed || document.hidden || operationRef.current || contacting) return
      // 숨겨진 탭에서는 접속 표시를 보내지 않는다. 절전·네트워크 단절 뒤에는 먼저 캐치업한다.
      const reconnect = resume || needsResume || Date.now() - lastContact >= 60000
      contacting = true
      if (reconnect) {
        operationRef.current = true
        setLoading(true)
      }
      try {
        const response = await fetch(`/api/chat/${character.id}/conversations/${roomId}/${reconnect ? "resume" : "presence"}`, {
          method: "POST", signal: AbortSignal.timeout(15000),
        })
        if (!response.ok) throw new Error("접속 상태를 갱신하지 못했습니다.")
        lastContact = Date.now()
        needsResume = false
        if (reconnect && !disposed) {
          setProgress(await response.json())
          setDebugInfo(null)
          await loadMessages()
        }
      } catch {
        needsResume = true
        if (!disposed) setStartError("접속 상태를 확인하지 못했습니다. 연결 후 자동으로 다시 확인합니다.")
      } finally {
        contacting = false
        if (reconnect) operationRef.current = false
        if (reconnect && !disposed) setLoading(false)
      }
    }
    const onVisible = () => contact(true)
    const timer = window.setInterval(() => contact(false), 30000)
    document.addEventListener("visibilitychange", onVisible)
    return () => {
      disposed = true
      window.clearInterval(timer)
      document.removeEventListener("visibilitychange", onVisible)
    }
  }, [session?.conversationId, selectedProfileId, pickerMode, character.id, loadMessages])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({
      behavior: messages.length > 0 ? "smooth" : "auto",
    })
  }, [messages, sending])


  async function sendMessage() {
    const message = input.trim()
    const isContinuation = !message

    if (sending || loading || pickerMode || !session?.playerProfile || operationRef.current) {
      return
    }

    operationRef.current = true
    setSending(true)
    setInput("")

    const pendingMessage = isContinuation
        ? null
        : {
          id: `pending-${Date.now()}`,
          role: "USER",
          message,
          contentMode: "NORMAL",
          createdAt: new Date().toISOString(),
          pending: true,
        }

    if (pendingMessage) {
      setMessages((current) => [...current, pendingMessage])
    }

    try {
      const response = await fetch(world ? `/api/worlds/${world.id}/characters/${character.id}/chat` : `/api/chat/${character.id}`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          message,
          contentMode: "NORMAL",
        }),
      })

      if (!response.ok) {
        throw new Error(`메시지 전송 실패: ${response.status}`)
      }

      const data = await response.json()

      setDebugInfo(data.debug ?? null)

      // 서버가 USER + CHARACTER 메시지를 모두 저장하므로
      // 응답 성공 후 서버 기록을 다시 읽어 DB 상태와 화면을 맞춘다.
      await loadMessages()
    } catch (error) {
      console.error(error)

      if (pendingMessage) {
        setMessages((current) =>
            current.filter((item) => item.id !== pendingMessage.id)
        )
      }

      setInput(message)
    } finally {
      operationRef.current = false
      setSending(false)
    }
  }

  function startConversation() {
    if (loading || sending || operationRef.current) return
    if (!window.confirm("같은 캐릭터로 처음부터 시작할까요?\n기존 대화는 보관되며, 새 대화에는 이전 메시지와 기억이 이어지지 않습니다.\n작성 중인 메시지는 비워집니다. 이전 방 목록은 아직 제공되지 않습니다.")) return
    setStartError("")
    setPickerMode("new")
  }

  async function selectProfile(profile) {
    if (operationRef.current) return
    // 상태 반영 전 연속 클릭과 전송 요청이 겹치지 않도록 즉시 잠근다.
    operationRef.current = true
    setStarting(true)
    setStartError("")
    try {
      const newRoom = pickerMode === "new"
      const url = world
        ? newRoom
          ? `/api/worlds/${world.id}/characters/${character.id}/conversations`
          : `/api/worlds/${world.id}/characters/${character.id}/player-profile`
        : newRoom ? `/api/chat/${character.id}/conversations`
          : `/api/chat/${character.id}/conversations/${session.conversationId}/player-profile`
      const response = await fetch(url, {
        method: newRoom ? "POST" : "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ playerProfileId: profile?.id ?? null }),
      })
      if (!response.ok) throw new Error(`프로필 지정 실패: ${response.status}`)
      const data = await response.json()
      // 생성 응답의 인트로로 바로 전환하여 이전 방의 Inspector도 함께 비운다.
      if (newRoom) {
        setMessages(data.messages)
        setInput("")
      }
      setSession({ conversationId: data.conversationId, playerProfile: profile ?? null })
      setDebugInfo(null)
      setPickerMode(null)
      await loadMessages(true)
    } catch (error) {
      console.error(error)
      setStartError("프로필 지정 결과를 확인하지 못했습니다. 새로고침해 현재 대화를 확인해 주세요.")
    } finally {
      operationRef.current = false
      setStarting(false)
    }
  }

  async function toggleProgress() {
    if (operationRef.current || !session || !progress) return
    operationRef.current = true
    setStarting(true)
    try {
      const response = await fetch(`/api/chat/${character.id}/conversations/${session.conversationId}/auto-progress`, {
        method: "PUT", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ enabled: !progress.enabled }), signal: AbortSignal.timeout(15000),
      })
      if (!response.ok) throw new Error("자동 진행 설정 실패")
      setProgress(await response.json())
      setStartError("")
    } catch {
      setStartError("자동 진행 설정을 확인하지 못했습니다. 새로고침 후 상태를 확인해 주세요.")
    } finally {
      operationRef.current = false
      setStarting(false)
    }
  }

  function handleKeyDown(event) {
    if (enterSendsMessage(event,chatInputEnvironment())) {
      event.preventDefault()
      sendMessage()
    }
  }

  if (pickerMode) {
    return <main className="min-h-svh bg-background text-foreground">
      <p className="mx-auto max-w-2xl px-4 pt-6 text-sm text-muted-foreground">{character.name} · {pickerMode === "new" ? "새 대화 시작" : "유저 프로필 선택"}</p>
      {startError && <p role="alert" className="mx-auto max-w-2xl px-4 py-2 text-destructive">{startError}</p>}
      <PlayerProfilePicker current={session?.playerProfile} busy={starting}
        onSelect={selectProfile}
        onSaved={profile => {
          if (session?.playerProfile?.id === profile.id) setSession({ ...session, playerProfile: profile })
        }}
        onCancel={() => session?.playerProfile ? setPickerMode(null) : onBack()} />
    </main>
  }

  return (
    <main className="flex h-svh bg-background text-foreground">
      <section className="mx-auto flex h-full w-full max-w-4xl flex-col border-x bg-card">
        <header className="flex min-h-16 shrink-0 flex-wrap items-center gap-3 border-b px-4 py-2 sm:px-6">

          <Button
            type="button"
            variant="ghost"
            size="icon"
            onClick={onBack}
            disabled={sending || starting}
            aria-label="캐릭터 목록으로 돌아가기"
          >
            <ArrowLeft className="size-4" />
          </Button>

          <Avatar>
            <AvatarFallback className="bg-accent text-accent-foreground">
              {character.name.slice(0, 1).toUpperCase()}
            </AvatarFallback>
          </Avatar>

          <div className="min-w-0">
            <h1 className="font-medium leading-none">
              {character.name}
            </h1>
            <p className="mt-1 text-xs text-muted-foreground">
              {world ? `${world.name} · WORLD` : "PocketMM"}
            </p>
          </div>
          <Button type="button" variant="ghost" className={world ? "ml-auto shrink-0" : ""} disabled={loading || sending || starting || !session}
            onClick={() => { setStartError(""); setPickerMode("select") }}>
            {session?.playerProfile?.avatar
              ? <img src={session.playerProfile.avatar} alt="" className="size-7 rounded-full object-cover" />
              : <span className="grid size-7 place-items-center rounded-full bg-accent text-xs text-accent-foreground">{session?.playerProfile?.name?.slice(0, 1) ?? "나"}</span>}
            {session?.playerProfile?.name ?? "프로필 선택"}
          </Button>
          <Button type="button" variant="outline" className={world ? "shrink-0" : "ml-auto shrink-0"}
            onClick={startConversation} disabled={loading || sending || starting || !session}>
            {starting ? "시작 중..." : "새 대화 시작"}
          </Button>
        </header>

        {startError && <p role="alert" className="px-4 py-2 text-sm text-destructive">{startError}</p>}

        {!world && progress && <div className="shrink-0 border-b px-4 py-2 text-xs text-muted-foreground sm:px-6">
          <div className="flex items-center gap-3">
            <Button type="button" variant="outline" size="sm" onClick={toggleProgress}
              disabled={loading || sending || starting || !session?.playerProfile}>
              자동 진행 {progress.enabled ? "ON" : "OFF"}
            </Button>
            <span>재입장 시 부재 시간 반영 · 최대 3일 · 휴식 규칙</span>
          </div>
          <p className="mt-2">현재 상태 · 에너지 {progress.energy} / 기분 {progress.mood}</p>
          {progress.pauseReason && <p role="status" className="mt-2">{progress.pauseReason} 재개하려면 OFF 후 다시 켜 주세요.</p>}
          {progress.lastReport && <details className="mt-2">
            <summary className="cursor-pointer">최근 자동 진행: {Math.floor(progress.lastReport.appliedSeconds / 60)}분</summary>
            <p>{progress.lastReport.from?.replace("T", " ")} → {progress.lastReport.to?.replace("T", " ")}</p>
            <p className="whitespace-pre-wrap">{progress.lastReport.actions || "이 구간에 발생한 자동 행동은 없습니다."}</p>
            {progress.lastReport.capped && <p>3일까지만 반영했습니다. 초과 시간은 다음 접속에 이월하지 않습니다.</p>}
            {progress.lastReport.pauseReason && <p>{progress.lastReport.pauseReason}</p>}
          </details>}
        </div>}

        {!world && <CustomStatePanel
          characterId={character.id}
          conversationId={session?.conversationId}
        />}

        {debugInfo && (
          <div className="shrink-0 border-b bg-muted/40 px-4 py-2 font-mono text-[11px] text-muted-foreground sm:px-6">
            <div className="mx-auto grid max-w-3xl grid-cols-2 gap-x-4 gap-y-1 sm:grid-cols-3">
              <span>
                SERVER: {debugInfo.serverTime ?? "-"}
              </span>

              <span>
                AI CTX: {debugInfo.aiContextTime ?? "-"}
              </span>

              <span>
                TIME: {debugInfo.interpreter ?? "-"}
                {debugInfo.precision && ` · ${debugInfo.precision}`}
              </span>

              <span>
                EXPECTED: {formatElapsedMinutes(debugInfo.expectedElapsedMinutes)}
              </span>

              <span>
                SCALE: {debugInfo.durationScale ?? "-"}
              </span>

              <span>
                APPLIED: {formatElapsedMinutes(debugInfo.appliedElapsedMinutes)}
              </span>

              <span>
                PREV: {debugInfo.previousTime ?? "-"}
              </span>

              <span>
                NEW: {debugInfo.newTime ?? "-"}
              </span>

              <span className="col-span-2 break-words sm:col-span-3">
                SRC: {debugInfo.sourceExpression ?? "-"}
              </span>
              <details className="col-span-2 max-h-36 overflow-y-auto break-words sm:col-span-3">
                <summary className="cursor-pointer">
                  MEMORY: {debugInfo.memoryStatus ?? "-"} · 저장 {debugInfo.savedMemories?.length ?? 0} · 회상 {debugInfo.recalledMemories?.length ?? 0}
                </summary>
                <p className="mt-1">회상: 이번 응답에 전달된 기존 기억 / 저장: 다음 턴부터 사용</p>
                {debugInfo.recalledMemories?.map((memory, index) => (
                  <p key={`recalled-${index}`} className="mt-1">[회상] {memory}</p>
                ))}
                {debugInfo.savedMemories?.map((memory, index) => (
                  <p key={`saved-${index}`} className="mt-1">[저장] {memory}</p>
                ))}
              </details>
              {debugInfo.stateUpdate && <details className="col-span-2 max-h-36 overflow-y-auto break-words sm:col-span-3">
                <summary className="cursor-pointer">
                  STATE: {debugInfo.stateUpdate.status} · 에너지 {debugInfo.stateUpdate.previousEnergy} → {debugInfo.stateUpdate.energy}
                  {" / "}기분 {debugInfo.stateUpdate.previousMood} → {debugInfo.stateUpdate.mood}
                </summary>
                {debugInfo.stateUpdate.energyEvidence && <p className="mt-1">에너지 근거: {debugInfo.stateUpdate.energyEvidence}</p>}
                {debugInfo.stateUpdate.moodEvidence && <p className="mt-1">기분 근거: {debugInfo.stateUpdate.moodEvidence}</p>}
                {debugInfo.stateUpdate.status === "REJECTED" && <p className="mt-1">상태 제안의 형식·범위·본문 근거가 맞지 않아 기존 상태를 유지했습니다.</p>}
              </details>}
            </div>
          </div>
        )}

        <ScrollArea className="min-h-0 flex-1">
          <div className="mx-auto flex w-full max-w-3xl flex-col gap-4 p-4 pb-32 sm:p-6 sm:pb-36">
            {loading && (
              <p className="py-10 text-center text-sm text-muted-foreground">
                대화를 불러오는 중...
              </p>
            )}

            {!loading &&
              messages.map((item) => {
                const isUser = item.role === "USER"

                return (
                  <article
                    key={item.id}
                    className="border-b border-border/70 py-7 last:border-b-0 sm:py-9"
                  >
                    <RpText
                      text={item.message}
                      isUser={isUser}
                    />
                  </article>
                )
              })}

            {sending && (
              <div className="border-b border-border/70 py-7 sm:py-9">
                <p className="animate-pulse text-sm italic text-muted-foreground">
                  장면을 이어가는 중...
                </p>
              </div>
            )}

            <div ref={bottomRef} />
          </div>
        </ScrollArea>

        <div className="shrink-0 border-t bg-card p-3 sm:p-4">
          <div className="mx-auto flex max-w-3xl items-end gap-2">
            <Textarea
              value={input}
              onChange={(event) => setInput(event.target.value)}
              onKeyDown={handleKeyDown}
              placeholder={world ? `${world.name}에서 ${character.name}에게 말하기` : `${character.name}에게 메시지 보내기`}
              disabled={loading || sending || starting || !session?.playerProfile}
              rows={1}
              className="max-h-36 min-h-11 resize-none text-base"
            />

            <Button
              type="button"
              size="icon"
              className="size-11 shrink-0"
              disabled={loading || sending || starting || !session?.playerProfile}
              onClick={sendMessage}
              aria-label="메시지 보내기"
            >
              <Send className="size-4" />
            </Button>
          </div>

          <p className="mx-auto mt-2 max-w-3xl text-center text-xs text-muted-foreground">
            Enter 전송 · Shift + Enter 줄바꿈
          </p>
        </div>
      </section>
    </main>
  )
}
