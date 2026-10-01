import { useEffect, useState } from "react"
import {
  Clock3,
  Home,
  MessageCircle,
  Moon,
  Settings,
  Sparkles,
  Sun,
  Users,
} from "lucide-react"

import PocketIcon from "@/components/PocketIcon"
import "./HomePage.css"

const activityItems = [
  { time: "방금", text: "작은 세계가 다음 장면을 준비하고 있어요." },
  { time: "12분 전", text: "캐릭터의 최근 행동이 정리되었어요." },
  { time: "오늘", text: "새로운 기억 후보가 생겼어요." },
]

const memoryItems = [
  { label: "최근 기억", text: "오늘 있었던 일을 천천히 정리하고 있어요." },
  { label: "관계 변화", text: "말투와 행동에서 작은 변화가 감지되었어요." },
]

const navItems = [
  { id: "home", label: "NOW", icon: Home },
  { id: "characters", label: "캐릭터", icon: Users },
  { id: "timeline", label: "세계", icon: Clock3 },
  { id: "pockets", label: "포켓", icon: PocketIcon },
  { id: "settings", label: "설정", icon: Settings },
]

export default function HomePage({ onOpenCharacters, onOpenWorld, onOpenPockets }) {
  const [dark, setDark] = useState(() =>
    document.documentElement.classList.contains("dark")
  )

  useEffect(() => {
    document.documentElement.classList.toggle("dark", dark)
  }, [dark])

  function selectNav(id) {
    if (id === "characters") {
      onOpenCharacters()
    }
    if (id === "timeline") {
      onOpenWorld()
    }
    if (id === "pockets") {
      onOpenPockets()
    }
  }

  return (
    <div className="now-shell">
      <aside className="now-sidebar">
        <div className="brand-block">
          <div className="logo-placeholder" aria-label="PocketMM logo placeholder" />
          <div>
            <strong>PocketMM</strong>
            <span>Living character world</span>
          </div>
        </div>

        <nav className="desktop-nav" aria-label="Primary navigation">
          {navItems.map(({ id, label, icon: Icon }) => (
            <button
              className={id === "home" ? "nav-item active" : "nav-item"}
              key={id}
              onClick={() => selectNav(id)}
              type="button"
            >
              <Icon size={18} strokeWidth={1.8} />
              <span>{label}</span>
            </button>
          ))}
        </nav>

        <button
          className="theme-button"
          onClick={() => setDark((value) => !value)}
          type="button"
        >
          {dark ? <Sun size={17} /> : <Moon size={17} />}
          <span>{dark ? "Light mode" : "Dark mode"}</span>
        </button>
      </aside>

      <main className="now-main">
        <header className="mobile-header">
          <div className="brand-block">
            <div className="logo-placeholder" aria-label="PocketMM logo placeholder" />
            <strong>PocketMM</strong>
          </div>
          <button
            className="icon-button"
            onClick={() => setDark((value) => !value)}
            type="button"
            aria-label="테마 전환"
          >
            {dark ? <Sun size={19} /> : <Moon size={19} />}
          </button>
        </header>

        <section className="world-column">
          <div className="section-heading">
            <div>
              <span className="eyebrow">NOW</span>
              <h1>지금, 작은 세계에서는</h1>
              <p>캐릭터들의 현재 생활과 최근 변화를 한눈에 확인해요.</p>
            </div>
            <span className="live-status"><span /> LIVE</span>
          </div>

          <div className="world-viewport">
            <div className="viewport-grid" />
            <div className="viewport-placeholder">
              <Sparkles size={24} strokeWidth={1.5} />
              <strong>3D World Viewport</strong>
              <span>Godot 생활 세계가 들어갈 자리</span>
            </div>
            <div className="world-caption">
              <div>
                <span className="caption-label">현재 장면</span>
                <strong>조용한 오후를 보내는 중</strong>
              </div>
              <button className="enter-button" type="button">
                <MessageCircle size={16} />
                대화하기
              </button>
            </div>
          </div>

          <section className="mobile-feed">
            <ActivityPanel />
            <MemoryPanel />
          </section>
        </section>
      </main>

      <aside className="now-right-panel">
        <ActivityPanel />
        <MemoryPanel />
      </aside>

      <nav className="bottom-nav" aria-label="Mobile navigation">
        {navItems.slice(0, 4).map(({ id, label, icon: Icon }) => (
          <button
            className={id === "home" ? "bottom-nav-item active" : "bottom-nav-item"}
            key={id}
            onClick={() => selectNav(id)}
            type="button"
          >
            <Icon size={19} strokeWidth={1.8} />
            <span>{label}</span>
          </button>
        ))}
      </nav>
    </div>
  )
}

function ActivityPanel() {
  return (
    <section className="info-panel">
      <div className="panel-title">
        <div>
          <span className="eyebrow">ACTIVITY</span>
          <h2>지금, 이런 일이 있어요</h2>
        </div>
      </div>
      <div className="activity-list">
        {activityItems.map((item) => (
          <article className="activity-row" key={item.time + item.text}>
            <span className="activity-dot" />
            <div>
              <p>{item.text}</p>
              <time>{item.time}</time>
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}

function MemoryPanel() {
  return (
    <section className="info-panel">
      <div className="panel-title">
        <div>
          <span className="eyebrow">MEMORY</span>
          <h2>새로운 기억</h2>
        </div>
        <span className="memory-badge">2</span>
      </div>
      <div className="memory-list">
        {memoryItems.map((item) => (
          <article className="memory-row" key={item.label}>
            <span>{item.label}</span>
            <p>{item.text}</p>
          </article>
        ))}
      </div>
    </section>
  )
}
