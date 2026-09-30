import { useEffect, useState } from "react"

import CharacterCreatePage from "@/pages/CharacterCreatePage"
import CharacterPage from "@/pages/CharacterPage"
import ChatPage from "@/pages/ChatPage"
import HomePage from "@/pages/HomePage"
import LoginPage from "@/pages/LoginPage"
import SignupPage from "@/pages/SignupPage"
import WorldPage from "@/pages/WorldPage"

const routePaths = { home: "/", characters: "/characters", world: "/worlds" }\n\nfunction pageFromPath(pathname) {\n  if (pathname.startsWith("/worlds")) return "world"\n  if (pathname.startsWith("/characters")) return "characters"\n  return "home"\n}\n\nexport default function App() {
  const [page, setPage] = useState(() => pageFromPath(window.location.pathname))
  const [selectedCharacter, setSelectedCharacter] = useState(null)
  const [member, setMember] = useState(null)
  const [authLoading, setAuthLoading] = useState(true)

  async function refreshMember() {
    try {
      const response = await fetch("/api/auth/me", { credentials: "include" })
      if (!response.ok) {
        setMember(null)
        return false
      }
      setMember(await response.json())
      setPage(pageFromPath(window.location.pathname))
      return true
    } finally {
      setAuthLoading(false)
    }
  }

  useEffect(() => { refreshMember() }, [])\n\n  useEffect(() => {\n    const handlePopState = () => {\n      setSelectedCharacter(null)\n      setPage(pageFromPath(window.location.pathname))\n    }\n    window.addEventListener("popstate", handlePopState)\n    return () => window.removeEventListener("popstate", handlePopState)\n  }, [])\n\n  function navigate(nextPage) {\n    const path = routePaths[nextPage] ?? "/"\n    if (window.location.pathname !== path) window.history.pushState({}, "", path)\n    setPage(nextPage)\n  }

  function openChat(character) { setSelectedCharacter(character); setPage("chat") }
  function openCharacters() { setSelectedCharacter(null); navigate("characters") }
  function openEdit(character) { setSelectedCharacter(character); setPage("edit") }
  function finishEdit(character) { setSelectedCharacter(character); setPage("characters") }

  if (authLoading) {
    return <main className="grid min-h-svh place-items-center bg-background text-sm text-muted-foreground">PocketMM을 준비하는 중...</main>
  }

  if (!member) {
    if (page === "signup") {
      return <SignupPage onOpenLogin={() => setPage("login")} />
    }
    return <LoginPage onAuthenticated={refreshMember} onOpenSignup={() => setPage("signup")} />
  }

  if (page === "home") return <HomePage onOpenCharacters={openCharacters} onOpenWorld={() => navigate("world")} />
  if (page === "world") return <WorldPage onBack={() => navigate("home")} />
  if (page === "create") return <CharacterCreatePage onBack={openCharacters} onCreated={openChat} />
  if (page === "edit" && selectedCharacter) {
    return <CharacterCreatePage character={selectedCharacter} onBack={openCharacters} onUpdated={finishEdit} />
  }
  if (page === "chat" && selectedCharacter) {
    return <ChatPage character={selectedCharacter} onBack={openCharacters} />
  }

  return (
    <CharacterPage
      onSelectCharacter={openChat}
      onCreateCharacter={() => setPage("create")}
      onEditCharacter={openEdit}
    />
  )
}
