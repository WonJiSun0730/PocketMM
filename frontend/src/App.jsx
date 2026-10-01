import { useEffect, useState } from "react"

import CharacterCreatePage from "@/pages/CharacterCreatePage"
import CharacterPage from "@/pages/CharacterPage"
import ChatPage from "@/pages/ChatPage"
import HomePage from "@/pages/HomePage"
import LoginPage from "@/pages/LoginPage"
import PocketPage from "@/pages/PocketPage"
import PocketsPage from "@/pages/PocketsPage"
import PlayerProfilesPage from "@/pages/PlayerProfilesPage"
import SignupPage from "@/pages/SignupPage"
import WorldPage from "@/pages/WorldPage"

const routePaths = { home: "/", characters: "/characters", world: "/worlds", pockets: "/pockets", playerProfiles: "/pockets/profiles" }

function pageFromPath(pathname) {
  if (pathname.startsWith("/pockets/profiles")) return "playerProfiles"
  if (pathname.startsWith("/pockets")) return "pockets"
  if (pathname.startsWith("/worlds")) return "world"
  if (pathname.startsWith("/characters")) return "characters"
  return "home"
}

export default function App() {
  const [page, setPage] = useState(() => pageFromPath(window.location.pathname))
  const [selectedCharacter, setSelectedCharacter] = useState(null)
  const [selectedWorldChat, setSelectedWorldChat] = useState(null)
  const [selectedPocket, setSelectedPocket] = useState(null)
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

  useEffect(() => { refreshMember() }, [])

  useEffect(() => {
    const handlePopState = () => {
      setSelectedCharacter(null)
      setPage(pageFromPath(window.location.pathname))
    }
    window.addEventListener("popstate", handlePopState)
    return () => window.removeEventListener("popstate", handlePopState)
  }, [])

  function navigate(nextPage) {
    const path = routePaths[nextPage] ?? "/"
    if (window.location.pathname !== path) window.history.pushState({}, "", path)
    setPage(nextPage)
  }

  function openChat(character) { setSelectedWorldChat(null); setSelectedCharacter(character); setPage("chat") }
  function openWorldChat(world, character) { setSelectedWorldChat(world); setSelectedCharacter(character); setPage("worldChat") }
  function openPocket(pocket) { setSelectedPocket(pocket); setPage("pocket") }
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

  if (page === "home") return <HomePage onOpenCharacters={openCharacters} onOpenWorld={() => navigate("world")} onOpenPockets={() => navigate("pockets")} />
  if (page === "world") return <WorldPage onBack={() => navigate("home")} onOpenWorldChat={openWorldChat} onOpenPocket={openPocket} />
  if (page === "pocket" && selectedPocket) return <PocketPage pocket={selectedPocket} onBack={() => navigate("pockets")} />
  if (page === "pockets") return <PocketsPage onBack={() => navigate("home")} onOpenPocket={openPocket} onManageProfiles={() => navigate("playerProfiles")} />
  if (page === "playerProfiles") return <PlayerProfilesPage onBack={() => navigate("pockets")} />
  if (page === "create") return <CharacterCreatePage onBack={openCharacters} onCreated={openChat} />
  if (page === "edit" && selectedCharacter) {
    return <CharacterCreatePage character={selectedCharacter} onBack={openCharacters} onUpdated={finishEdit} />
  }
  if (page === "chat" && selectedCharacter) {
    return <ChatPage character={selectedCharacter} onBack={openCharacters} />
  }
  if (page === "worldChat" && selectedCharacter && selectedWorldChat) {
    return <ChatPage character={selectedCharacter} world={selectedWorldChat} onBack={() => setPage("world")} />
  }

  return (
    <CharacterPage
      onSelectCharacter={openChat}
      onCreateCharacter={() => setPage("create")}
      onEditCharacter={openEdit}
    />
  )
}
