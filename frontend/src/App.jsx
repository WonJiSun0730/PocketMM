import { useState } from "react"

import CharacterCreatePage from "@/pages/CharacterCreatePage"
import CharacterPage from "@/pages/CharacterPage"
import ChatPage from "@/pages/ChatPage"

export default function App() {
  const [page, setPage] = useState("characters")
  const [selectedCharacter, setSelectedCharacter] = useState(null)

  function openChat(character) {
    setSelectedCharacter(character)
    setPage("chat")
  }

  function openCharacters() {
    setSelectedCharacter(null)
    setPage("characters")
  }

  function openEdit(character) {
    setSelectedCharacter(character)
    setPage("edit")
  }

  function finishEdit(character) {
    setSelectedCharacter(character)
    setPage("characters")
  }

  if (page === "create") {
    return (
      <CharacterCreatePage
        onBack={openCharacters}
        onCreated={openChat}
      />
    )
  }

  if (page === "edit" && selectedCharacter) {
    return (
      <CharacterCreatePage
        character={selectedCharacter}
        onBack={openCharacters}
        onUpdated={finishEdit}
      />
    )
  }

  if (page === "chat" && selectedCharacter) {
    return (
      <ChatPage
        character={selectedCharacter}
        onBack={openCharacters}
      />
    )
  }

  return (
    <CharacterPage
      onSelectCharacter={openChat}
      onCreateCharacter={() => setPage("create")}
      onEditCharacter={openEdit}
    />
  )
}