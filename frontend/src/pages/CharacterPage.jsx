import { useEffect, useState } from "react"
import { Plus } from "lucide-react"

import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"

export default function CharacterPage({
  onSelectCharacter,
  onCreateCharacter,
  onEditCharacter,
}) {
  const [characters, setCharacters] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    loadCharacters()
  }, [])

  async function loadCharacters() {
    try {
      const response = await fetch("/api/characters")

      if (!response.ok) {
        throw new Error(`캐릭터 조회 실패: ${response.status}`)
      }

      const data = await response.json()
      setCharacters(data)
    } catch (error) {
      console.error(error)
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="min-h-svh bg-background px-4 py-10 text-foreground sm:px-6">
      <section className="mx-auto w-full max-w-4xl">
        <header className="mb-8 flex items-end justify-between gap-4">
          <div>
            <p className="mb-2 text-sm text-muted-foreground">
              PocketMM
            </p>

            <h1 className="text-3xl font-semibold tracking-tight">
              캐릭터
            </h1>

            <p className="mt-2 text-sm text-muted-foreground">
              함께할 캐릭터를 선택하세요.
            </p>
          </div>

          <Button
            type="button"
            onClick={onCreateCharacter}
          >
            <Plus className="size-4" />
            새 캐릭터
          </Button>
        </header>

        {loading ? (
          <p className="py-20 text-center text-sm text-muted-foreground">
            캐릭터를 불러오는 중...
          </p>
        ) : (
          <div className="grid gap-3 sm:grid-cols-2">
            {characters.map((character) => (
              <div
                key={character.id}
                className="flex items-center gap-3 rounded-xl border bg-card p-4"
              >
                <button
                  type="button"
                  onClick={() => onSelectCharacter(character)}
                  className="flex min-w-0 flex-1 items-center gap-4 text-left"
                >
                  <Avatar className="size-12">
                    <AvatarFallback>
                      {character.name.slice(0, 1).toUpperCase()}
                    </AvatarFallback>
                  </Avatar>

                  <div className="min-w-0">
                    <h2 className="truncate font-medium">
                      {character.name}
                    </h2>

                    <p className="mt-1 line-clamp-2 text-sm text-muted-foreground">
                      {character.description ||
                        character.personality ||
                        "아직 프로필이 작성되지 않았어요."}
                    </p>
                  </div>
                </button>

                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => onEditCharacter(character)}
                >
                  수정
                </Button>
              </div>
            ))}

            {characters.length === 0 && (
              <div className="col-span-full rounded-xl border border-dashed p-10 text-center">
                <p className="text-sm text-muted-foreground">
                  아직 캐릭터가 없어요.
                </p>
              </div>
            )}
          </div>
        )}
      </section>
    </main>
  )
}