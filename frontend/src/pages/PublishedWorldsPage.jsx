import { useEffect, useState } from "react"
import { ArrowLeft, BookOpen, Home, MessageCircle, Moon, Sun, Upload, Users } from "lucide-react"
import { Button } from "@/components/ui/button"
import PlayerProfilePicker from "@/components/PlayerProfilePicker"
import RpText from "@/components/RpText"
import "./PublishedWorldsPage.css"

const selectedFromUrl = () => new URLSearchParams(window.location.search).get("world")
async function read(response) {
  if (!response.ok) throw new Error(response.status === 404 ? "배포가 종료되었거나 찾을 수 없는 월드예요." : "불러오지 못했어요. 잠시 후 다시 시도해 주세요.")
  return response.json()
}
function Tags({ tags }) { return <div className="published-tags">{tags.map(tag => <span key={tag}>#{tag}</span>)}</div> }
function Cover({ world, large = false }) {
  return world.coverImageUrl ? <img className={large ? "published-cover large" : "published-cover"} src={world.coverImageUrl} alt={`${world.name} 표지`} loading={large ? "eager" : "lazy"} /> : <div className="published-cover published-placeholder"><BookOpen aria-hidden="true" /><span>{world.name}</span></div>
}
export default function PublishedWorldsPage({ onNavigate, onOpenPocket }) {
  const [selected, setSelected] = useState(selectedFromUrl)
  const [listing, setListing] = useState({ items: [], page: 0, hasNext: false })
  const [detail, setDetail] = useState(null)
  const [introId, setIntroId] = useState("")
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")
  const [picker, setPicker] = useState(false)
  const [busy, setBusy] = useState(false)
  const [dark, setDark] = useState(() => document.documentElement.classList.contains("dark"))
  const [retry, setRetry] = useState(0)
  useEffect(() => { const pop = () => setSelected(selectedFromUrl()); window.addEventListener("popstate", pop); return () => window.removeEventListener("popstate", pop) }, [])
  useEffect(() => {
    const controller = new AbortController()
    setLoading(true); setError(""); setDetail(null); setIntroId(""); setPicker(false)
    fetch(selected ? `/api/published-worlds/${encodeURIComponent(selected)}` : `/api/published-worlds?page=${page}`, { signal: controller.signal }).then(read).then(data => {
      if (selected) { setDetail(data); setIntroId(data.intros[0]?.id?.toString() ?? "") }
      else setListing(data)
    }).catch(e => { if (e.name !== "AbortError") setError(e.message) }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [selected, page, retry])
  function open(id) {
    window.history.pushState({}, "", id ? `/deployments?world=${id}` : "/deployments")
    setSelected(id ? String(id) : null); window.scrollTo({ top: 0 })
  }
  async function start(profile) {
    if (busy || !introId) return
    setBusy(true); setError("")
    try {
      const response = await fetch(`/api/published-worlds/${selected}/start`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ introId: Number(introId), playerProfileId: profile?.id ?? null }) }).then(read)
      onOpenPocket({ world: { id: response.worldId, name: response.worldName }, room: { id: response.roomId, title: response.roomTitle }, session: response.session, playerProfile: profile ?? null })
    } catch (e) { setError(e.message) }
    finally { setBusy(false) }
  }
  const intro = detail?.intros.find(item => String(item.id) === introId)
  const navigation = [{ id: "home", label: "NOW", icon: Home }, { id: "characters", label: "캐릭터", icon: Users }, { id: "world", label: "세계", icon: BookOpen }, { id: "deployments", label: "배포", icon: Upload }, { id: "pockets", label: "포켓", icon: MessageCircle }]
  return <div className="published-shell">
    <aside className="published-sidebar"><strong>PocketMM</strong><nav aria-label="주 메뉴">{navigation.map(({ id, label, icon: Icon }) => <button key={id} type="button" aria-current={id === "deployments" ? "page" : undefined} onClick={() => id === "deployments" ? open(null) : onNavigate(id)}><Icon size={18} /><span>{label}</span></button>)}</nav><Button variant="ghost" onClick={() => { document.documentElement.classList.toggle("dark", !dark); setDark(!dark) }}>{dark ? <Sun size={18} /> : <Moon size={18} />}<span>{dark ? "라이트 모드" : "다크 모드"}</span></Button></aside>
    <main className="published-main">
      <header className="published-header"><div>{selected && <Button variant="ghost" onClick={() => open(null)}><ArrowLeft size={18} /> 배포 목록</Button>}<h1>{selected ? "월드 정보" : "배포"}</h1>{!selected && <p>다른 사람이 만든 세계에서 새로운 이야기를 시작하세요.</p>}</div>{!selected && <Button variant="outline" onClick={() => onNavigate("world")}>내 월드 관리</Button>}</header>
      {error && <div role="alert" className="published-error"><p>{error}</p>{!picker && <Button variant="outline" onClick={() => setRetry(n => n + 1)}>다시 불러오기</Button>}</div>}
      {loading ? <p role="status" className="published-empty">월드를 불러오는 중…</p> : !selected ? <>
        {!listing.items.length ? <div className="published-empty"><BookOpen size={32} /><h2>아직 배포된 월드가 없어요</h2><p>세계 메뉴에서 표지와 인트로를 준비한 뒤 첫 월드를 배포해 보세요.</p><Button onClick={() => onNavigate("world")}>세계로 이동</Button></div> : <div className="published-grid">{listing.items.map(world => <button type="button" className="published-card" key={world.id} onClick={() => open(world.id)}><Cover world={world} /><h2>{world.name}</h2><Tags tags={world.hashtags} /><div className="published-card-meta"><span><MessageCircle size={14} aria-hidden="true" /> {new Intl.NumberFormat("ko-KR", { notation: "compact" }).format(world.totalTurns)}턴</span><span>{world.publisher}</span></div></button>)}</div>}
        {(page > 0 || listing.hasNext) && <nav className="published-pagination" aria-label="목록 페이지"><Button variant="outline" disabled={page === 0} onClick={() => { setPage(p => p - 1); window.scrollTo({ top: 0 }) }}>이전</Button><span>{page + 1} 페이지</span><Button variant="outline" disabled={!listing.hasNext} onClick={() => { setPage(p => p + 1); window.scrollTo({ top: 0 }) }}>다음</Button></nav>}
      </> : detail && <>
        <section className="published-hero"><div className="published-cover-stage"><Cover world={detail.world} large /></div><div className="published-summary"><h2>{detail.world.name}</h2><p className="published-byline">배포자 {detail.world.publisher} <span>· 전체 대화 {detail.world.totalTurns.toLocaleString("ko-KR")}턴</span></p><Tags tags={detail.world.hashtags} /><p className="publication-help">마음에 드는 도입을 골라 나만의 새 포켓에서 시작하세요.</p></div></section>
        <section className="published-section"><h2>플레이가이드</h2><div className="published-guide">{detail.playGuide || "등록된 플레이가이드가 없어요."}</div></section>
        <section className="published-section"><h2>인트로 선택</h2><label className="published-select-label" htmlFor="published-intro">이야기의 시작</label><select id="published-intro" value={introId} onChange={e => setIntroId(e.target.value)} disabled={busy || picker}><option value="" disabled>인트로를 선택하세요</option>{detail.intros.map(item => <option key={item.id} value={item.id}>{item.title}</option>)}</select>{intro && <div className="published-intro-preview"><h3>도입 미리보기</h3><RpText text={intro.content} /></div>}{!detail.intros.length && <p>선택할 수 있는 인트로가 없어요.</p>}</section>
        {picker && <section className="published-profile"><PlayerProfilePicker busy={busy} onSelect={start} onCancel={() => setPicker(false)} /></section>}
        <footer className="published-start"><p>기존 포켓은 유지하고 새 이야기를 시작합니다.</p><Button disabled={!introId || busy || picker} onClick={() => setPicker(true)}>새로하기</Button></footer>
      </>}
    </main>
    <nav className="published-bottom" aria-label="모바일 주 메뉴">{navigation.map(({ id, label, icon: Icon }) => <button type="button" key={id} aria-current={id === "deployments" ? "page" : undefined} onClick={() => id === "deployments" ? open(null) : onNavigate(id)}><Icon size={20} /><span>{label}</span></button>)}</nav>
  </div>
}
