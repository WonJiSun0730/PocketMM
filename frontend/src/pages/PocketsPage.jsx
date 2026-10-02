import { useEffect, useState } from "react"
import { ArrowLeft, Trash2 } from "lucide-react"

function PocketIcon({ size = 28, strokeWidth = 1.4 }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M5 4.5h14l-1.2 15H6.2L5 4.5Z" />
    <path d="M8 9.5c1.1 1.1 2.45 1.65 4 1.65s2.9-.55 4-1.65" />
  </svg>
}

export default function PocketsPage({ onBack, onOpenPocket, onManageProfiles }) {
  const [pockets, setPockets] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")

  useEffect(() => {
    fetch("/api/worlds/pockets", { credentials: "include" })
      .then(async response => {
        if (!response.ok) throw new Error("포켓 목록을 불러오지 못했어요.")
        setPockets(await response.json())
      })
      .catch(error => setError(error.message))
      .finally(() => setLoading(false))
  }, [])

  async function openPocket(item) {
    setError("")
    try {
      const [sessionResponse, roomResponse] = await Promise.all([
        fetch(`/api/worlds/pockets/${item.sessionId}`, { credentials: "include" }),
        fetch(`/api/worlds/${item.worldId}/rooms/${item.roomId}`, { credentials: "include" }),
      ])
      if (!sessionResponse.ok || !roomResponse.ok) throw new Error("포켓을 열지 못했어요.")
      const session = await sessionResponse.json()
      const room = await roomResponse.json()
      onOpenPocket({
        world: { id: item.worldId, name: item.worldName },
        room,
        session,
        playerProfile: item.playerProfileId ? { id: item.playerProfileId, name: item.playerProfileName } : null,
      })
    } catch (error) {
      setError(error.message)
    }
  }

  async function deletePocket(item) {
    if (!window.confirm(`'${item.roomTitle}' 포켓을 삭제할까요? 저장된 대화와 이 포켓의 기억도 함께 삭제됩니다.`)) return
    setError("")
    try {
      const response = await fetch(`/api/worlds/pockets/${item.sessionId}`, { method: "DELETE", credentials: "include" })
      if (!response.ok) throw new Error("포켓을 삭제하지 못했어요.")
      setPockets(current => current.filter(pocket => pocket.sessionId !== item.sessionId))
    } catch (error) {
      setError(error.message)
    }
  }

  return <main className="min-h-svh bg-background px-4 py-8 text-foreground sm:px-8">
    <section className="mx-auto w-full max-w-4xl">
      <header className="mb-8 flex items-center gap-4">
        <button type="button" onClick={onBack} className="grid size-10 place-items-center rounded-xl border bg-card" aria-label="돌아가기"><ArrowLeft size={18}/></button>
        <div className="min-w-0 flex-1"><span className="text-[11px] font-bold tracking-[.14em] text-accent-foreground">POCKET</span><h1 className="mt-1 text-3xl font-medium">내 포켓</h1><p className="mt-1 text-sm text-muted-foreground">시작했던 이야기를 다시 이어가요.</p></div><button type="button" onClick={onManageProfiles} className="shrink-0 rounded-xl border bg-card px-4 py-2 text-sm font-medium hover:bg-accent">플레이어 프로필 관리</button>
      </header>
      {error && <p className="mb-4 text-sm text-destructive">{error}</p>}
      {loading ? <p className="py-16 text-center text-sm text-muted-foreground">포켓을 불러오는 중...</p> :
        pockets.length === 0 ? <div className="grid place-items-center gap-3 rounded-2xl border bg-card py-20 text-center"><PocketIcon /><strong>아직 포켓이 없어요.</strong><span className="text-sm text-muted-foreground">월드에서 인트로를 선택해 첫 포켓을 시작해 보세요.</span></div> :
        <div className="grid gap-3">{pockets.map(item => <div key={item.sessionId} className="flex overflow-hidden rounded-2xl border bg-card transition hover:bg-accent/50">
          <button type="button" onClick={() => openPocket(item)} className="grid min-w-0 flex-1 gap-2 p-5 text-left">
            <div className="flex items-start justify-between gap-4"><strong className="text-lg">{item.roomTitle}</strong><span className="shrink-0 text-xs text-muted-foreground">{item.playerProfileName ?? "프로필 없음"}</span></div>
            <span className="text-sm text-muted-foreground">{item.worldName}</span>
            <span className="text-xs text-muted-foreground">인트로 · {item.introTitle}</span>
          </button>
          <button type="button" onClick={() => deletePocket(item)} className="grid w-14 shrink-0 place-items-center border-l text-muted-foreground transition hover:bg-destructive/10 hover:text-destructive" aria-label={`${item.roomTitle} 포켓 삭제`}><Trash2 size={17}/></button>
        </div>)}</div>}
    </section>
  </main>
}
