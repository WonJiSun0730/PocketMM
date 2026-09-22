import { useEffect, useRef, useState } from "react"
import { Send } from "lucide-react"

import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Textarea } from "@/components/ui/textarea"

const CHARACTER_ID = 1

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

export default function ChatPage() {
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState("")
  const [loading, setLoading] = useState(true)
  const [sending, setSending] = useState(false)
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
      const response = await fetch(`/api/chat/${CHARACTER_ID}/messages`)

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
      const response = await fetch(`/api/chat/${CHARACTER_ID}`, {
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

      await response.json()

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
          <Avatar>
            <AvatarFallback className="bg-accent text-accent-foreground">
              M
            </AvatarFallback>
          </Avatar>

          <div className="min-w-0">
            <h1 className="font-medium leading-none">Millie</h1>
            <p className="mt-1 text-xs text-muted-foreground">
              PocketMM
            </p>
          </div>
        </header>

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
                <div className="mb-4 text-sm font-semibold text-foreground">
                  Millie
                </div>

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
              placeholder="Millie에게 메시지 보내기"
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