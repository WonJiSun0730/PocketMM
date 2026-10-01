import { useEffect, useState } from "react"
import { ArrowLeft, Send } from "lucide-react"

import "./PocketPage.css"

export default function PocketPage({ pocket, onBack }) {
  const { world, room, session, playerProfile } = pocket
  const base = `/api/worlds/${world.id}/rooms/${room.id}/sessions/${session.id}`
  const [messages, setMessages] = useState([])
  const [cast, setCast] = useState([])
  const [systems, setSystems] = useState([])
  const [draft, setDraft] = useState("")
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")

  async function refresh() {
    const [messageResponse, castResponse, systemResponse] = await Promise.all([
      fetch(`${base}/messages`, { credentials: "include" }),
      fetch(`${base}/cast`, { credentials: "include" }),
      fetch(`${base}/systems`, { credentials: "include" }),
    ])
    if (messageResponse.ok) setMessages(await messageResponse.json())
    if (castResponse.ok) setCast(await castResponse.json())
    if (systemResponse.ok) setSystems(await systemResponse.json())
  }

  useEffect(() => { refresh() }, [session.id])

  async function send(event) {
    event.preventDefault()
    const message = draft.trim()
    if (!message || busy) return
    setBusy(true)
    setError("")
    try {
      const response = await fetch(`${base}/chat`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ message }),
      })
      if (!response.ok) throw new Error("대화를 이어가지 못했어요.")
      setDraft("")
      await refresh()
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  return <main className="pocket-page">
    <header className="pocket-header">
      <button type="button" onClick={onBack}><ArrowLeft size={18}/> 월드로</button>
      <div><span>POCKET</span><h1>{room.title}</h1><p>{world.name} · {playerProfile?.name ?? "Player"}</p></div>
      <div className="pocket-status">
        {systems.map(system => <span key={system.id}>{system.name} <strong>{system.currentValue}</strong></span>)}
      </div>
    </header>

    <section className="pocket-stage">
      <article className="pocket-intro">
        <span>START SCENE</span>
        <h2>{session.introTitle}</h2>
        <p>{session.introContent}</p>
      </article>

      <div className="pocket-cast">{cast.length > 0 ? <>현재 장면 · {cast.map(item => item.name).join(" · ")}</> : "현재 장면 · 등장인물 없음"}</div>

      <div className="pocket-messages">
        {messages.map(message => <article className={`pocket-message ${String(message.role).toLowerCase()}`} key={message.id}>
          <span>{message.role === "USER" ? playerProfile?.name ?? "You" : "SCENE"}</span>
          <p>{message.content}</p>
        </article>)}
      </div>

      {error && <p className="pocket-error">{error}</p>}
      <form className="pocket-composer" onSubmit={send}>
        <textarea value={draft} onChange={e => setDraft(e.target.value)} disabled={busy} placeholder="행동이나 대사를 입력해 주세요." />
        <button type="submit" disabled={busy || !draft.trim()} aria-label="보내기"><Send size={18}/></button>
      </form>
    </section>
  </main>
}
