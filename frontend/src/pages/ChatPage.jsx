import { useEffect, useRef, useState } from "react"
import { ArrowLeft, Send } from "lucide-react"

import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Textarea } from "@/components/ui/textarea"

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
  onBack,
}) {
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState("")
  const [loading, setLoading] = useState(true)
  const [sending, setSending] = useState(false)
  const [debugInfo, setDebugInfo] = useState(null)
  const bottomRef = useRef(null)

  useEffect(() => {
    loadMessages()
  }, [])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({
      behavior: messages.length > 0 ? "smooth" : "auto",
    })
  }, [messages, sending])

  async function loadMessages() {
    try {
      const response = await fetch(`/api/chat/${character.id}/messages`)

      if (!response.ok) {
        throw new Error(`메시지 조회 실패: ${response.status}`)
      }

      const data = await response.json()
      setMessages(data)
    } catch (error) {
      console.error(error)
    } finally {
      setLoading(false)
    }
  }

  async function sendMessage() {
    const message = input.trim()

    if (!message || sending) {
      return
    }

    setSending(true)
    setInput("")

    const pendingMessage = {
      id: `pending-${Date.now()}`,
      role: "USER",
      message,
      contentMode: "NORMAL",
      createdAt: new Date().toISOString(),
      pending: true,
    }

    setMessages((current) => [...current, pendingMessage])

    try {
      const response = await fetch(`/api/chat/${character.id}`, {
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

      setMessages((current) =>
        current.filter((item) => item.id !== pendingMessage.id)
      )

      setInput(message)
    } finally {
      setSending(false)
    }
  }

  function handleKeyDown(event) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault()
      sendMessage()
    }
  }

  return (
    <main className="flex h-svh bg-background text-foreground">
      <section className="mx-auto flex h-full w-full max-w-4xl flex-col border-x bg-card">
        <header className="flex h-16 shrink-0 items-center gap-3 border-b px-4 sm:px-6">

          <Button
            type="button"
            variant="ghost"
            size="icon"
            onClick={onBack}
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
              PocketMM
            </p>
          </div>
        </header>

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
              placeholder={`${character.name}에게 메시지 보내기`}
              disabled={sending}
              rows={1}
              className="max-h-36 min-h-11 resize-none text-base"
            />

            <Button
              type="button"
              size="icon"
              className="size-11 shrink-0"
              disabled={sending || !input.trim()}
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
