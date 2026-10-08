import { useEffect, useState } from "react"
import { ArrowLeft, ChevronDown, ChevronUp } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"

export default function CharacterCreatePage({
  onBack,
  onCreated,
  character = null,
  onUpdated,
}) {
  const [name, setName] = useState("")
  const [description, setDescription] = useState("")
  const [rawDescription, setRawDescription] = useState("")
  const [examples, setExamples] = useState([""])

  const [personality, setPersonality] = useState("")
  const [speechStyle, setSpeechStyle] = useState("")
  const [background, setBackground] = useState("")
  const [appearance, setAppearance] = useState("")
  const [modelUrl, setModelUrl] = useState("")
  const [modelHeight, setModelHeight] = useState("")
  const [worlds, setWorlds] = useState([])
  const [worldId, setWorldId] = useState("")

  const [showDetails, setShowDetails] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")
  const editing = Boolean(character)

  useEffect(() => {
    fetch("/api/worlds", { credentials: "include" }).then((response) => response.ok ? response.json() : []).then(setWorlds).catch(() => setWorlds([]))
  }, [])

  useEffect(() => {
    if (!character) return

    setName(character.name ?? "")
    setDescription(character.description ?? "")
    setRawDescription(character.rawDescription ?? "")
    setExamples(
      character.examples?.length
        ? character.examples
        : [""]
    )
    setPersonality(character.personality ?? "")
    setSpeechStyle(character.speechStyle ?? "")
    setBackground(character.background ?? "")
    setAppearance(character.appearance ?? "")
    setModelUrl(character.modelUrl ?? "")
    setModelHeight(character.modelHeight ? String(character.modelHeight) : "")
    setWorldId(character.worldId ? String(character.worldId) : "")
  }, [character])

  const totalExampleLength = examples.reduce(
    (total, example) => total + example.length,
    0
  )

  const remainingExampleLength = 2000 - totalExampleLength

  function updateExample(index, value) {
    setExamples((current) => {
      const otherLength = current.reduce(
        (total, example, currentIndex) =>
          currentIndex === index
            ? total
            : total + example.length,
        0
      )

      const availableLength = Math.max(
        0,
        2000 - otherLength
      )

      return current.map((example, currentIndex) =>
        currentIndex === index
          ? value.slice(0, availableLength)
          : example
      )
    })
  }

  function addExample() {
    if (examples.length >= 10) {
      return
    }

    setExamples((current) => [...current, ""])
  }

  function removeExample(index) {
    setExamples((current) =>
      current.filter((_, currentIndex) => currentIndex !== index)
    )
  }

  async function saveCharacter() {
    const trimmedName = name.trim()

    if (!trimmedName || saving) {
      return
    }

    setSaving(true)
    setError("")

    const payload = {
      description: description.trim() || null,
      rawDescription: rawDescription.trim() || null,
      examples: examples
        .map((example) => example.trim())
        .filter(Boolean),
      exampleDialogue: null,
      personality: personality.trim() || null,
      speechStyle: speechStyle.trim() || null,
      background: background.trim() || null,
      appearance: appearance.trim() || null,
      modelUrl: modelUrl.trim() || null,
      modelHeight: modelUrl.trim() && modelHeight ? Number(modelHeight) : null,
    }

    try {
      const response = await fetch(
        editing
          ? `/api/characters/${character.id}/profile`
          : "/api/characters",
        {
          method: editing ? "PUT" : "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(
            editing
              ? payload
              : {
                  name: trimmedName,
                  ...payload,
                }
          ),
        }
      )

      if (!response.ok) {
        throw new Error(
          `캐릭터 ${editing ? "수정" : "생성"} 실패: ${response.status}`
        )
      }

      let savedCharacter = await response.json()

      const previousWorldId = character?.worldId == null ? "" : String(character.worldId)
      const worldChanged = editing ? worldId !== previousWorldId : Boolean(worldId)
      if (worldChanged) {
        const targetWorldIds = worlds.map((world) => world.id)
        for (const targetWorldId of targetWorldIds) {
          const assignedResponse = await fetch(`/api/worlds/${targetWorldId}/characters`, { credentials: "include" })
          if (!assignedResponse.ok) throw new Error("월드 연결 정보를 불러오지 못했어요.")
          const assigned = await assignedResponse.json()
          const ids = assigned.map((item) => item.id).filter((id) => id !== savedCharacter.id)
          if (String(targetWorldId) === worldId) ids.push(savedCharacter.id)
          if (assigned.some((item) => item.id === savedCharacter.id) || String(targetWorldId) === worldId) {
            const worldResponse = await fetch(`/api/worlds/${targetWorldId}/characters`, { method: "PUT", headers: { "Content-Type": "application/json" }, credentials: "include", body: JSON.stringify({ characterIds: ids }) })
            if (!worldResponse.ok) throw new Error("월드 연결을 저장하지 못했어요.")
          }
        }
      }

      savedCharacter = { ...savedCharacter, worldId: worldId ? Number(worldId) : null }

      if (editing) {
        onUpdated(savedCharacter)
      } else {
        onCreated(savedCharacter)
      }
    } catch (error) {
      console.error(error)
      setError(
        editing
          ? "캐릭터를 수정하지 못했어요. 잠시 후 다시 시도해 주세요."
          : "캐릭터를 만들지 못했어요. 잠시 후 다시 시도해 주세요."
      )
    } finally {
      setSaving(false)
    }
  }

  return (
    <main className="min-h-svh bg-background text-foreground">
      <section className="mx-auto w-full max-w-3xl px-4 py-6 sm:px-6 sm:py-10">
        <header className="mb-8">
          <Button
            type="button"
            variant="ghost"
            size="icon"
            onClick={onBack}
            className="mb-5"
            aria-label="캐릭터 목록으로 돌아가기"
          >
            <ArrowLeft className="size-5" />
          </Button>

          <p className="mb-2 text-sm text-muted-foreground">
            PocketMM Creator
          </p>

          <h1 className="text-3xl font-semibold tracking-tight">
            {editing ? "캐릭터 수정" : "캐릭터 만들기"}
          </h1>

          <p className="mt-2 text-sm leading-6 text-muted-foreground">
            형식에 맞출 필요 없이 캐릭터를 자유롭게 작성할 수 있어요.
          </p>
        </header>

        <div className="space-y-8">
          <section className="space-y-5 rounded-2xl border bg-card p-5 sm:p-6">
            <div>
              <h2 className="font-semibold">
                기본 설정
              </h2>
              <p className="mt-1 text-sm text-muted-foreground">
                캐릭터의 기본 정보를 작성하세요.
              </p>
            </div>

            <Field
              label="이름"
              required
              description="캐릭터의 이름"
            >
              <input
                value={name}
                disabled={editing}
                onChange={(event) => setName(event.target.value)}
                maxLength={100}
                placeholder="캐릭터 이름"
                className="flex h-11 w-full rounded-md border border-input bg-background px-3 text-sm outline-none transition-colors focus:border-ring"
              />
            </Field>

            <Field
              label="한줄 소개"
              description="캐릭터 목록 등에 표시되는 짧은 소개"
            >
              <input
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                placeholder="이 캐릭터를 짧게 소개해 주세요."
                className="flex h-11 w-full rounded-md border border-input bg-background px-3 text-sm outline-none transition-colors focus:border-ring"
              />
            </Field>

            <Field
              label="캐릭터 설명"
              description="성격, 말투, 외모, 과거, 관계, 특징 등 원하는 설정을 자유롭게 작성하세요."
            >
              <Textarea
                value={rawDescription}
                onChange={(event) =>
                  setRawDescription(event.target.value)
                }
                placeholder="정해진 형식은 없어요. 캐릭터에 대해 AI가 알아야 할 설정을 자유롭게 작성하세요."
                className="min-h-48 resize-y leading-6"
              />
            </Field>
          </section>

          <section className="space-y-5 rounded-2xl border bg-card p-5 sm:p-6">
            <div>
              <h2 className="font-semibold">
                대화 설정
              </h2>

              <p className="mt-1 text-sm leading-6 text-muted-foreground">
                캐릭터의 대화 스타일과 예시를 설정하세요.
              </p>
            </div>

            <Field
              label="예시 대화"
              description="여러 상황에서 캐릭터가 어떻게 말하고 행동하는지 보여주세요."
            >
              <div className="space-y-4">
                {examples.map((example, index) => (
                  <div
                    key={index}
                    className="rounded-xl border bg-background p-3"
                  >
                    <div className="mb-2 flex items-center justify-between gap-3">
                      <span className="text-xs font-medium text-muted-foreground">
                        예시 {index + 1}
                      </span>

                      <div className="flex items-center gap-3">
                        {examples.length > 1 && (
                          <button
                            type="button"
                            onClick={() => removeExample(index)}
                            className="text-xs text-muted-foreground hover:text-destructive"
                          >
                            삭제
                          </button>
                        )}
                      </div>
                    </div>

                    <Textarea
                      value={example}
                      onChange={(event) =>
                        updateExample(index, event.target.value)
                      }
                      placeholder={`*잠시 생각하던 캐릭터가 고개를 기울였다.*

            "그렇게 생각한 이유가 궁금하네요."

            *재촉하지 않고 조용히 대답을 기다렸다.*`}
                      className="min-h-40 resize-y leading-6"
                    />
                  </div>
                ))}

                <div className="flex items-center justify-between">
                  <div className="text-xs text-muted-foreground">
                    <span>{examples.length}/10개</span>
                    <span className="ml-3">
                      {totalExampleLength}/2000자
                    </span>
                  </div>

                  <Button
                    type="button"
                    variant="outline"
                    disabled={examples.length >= 10}
                    onClick={addExample}
                  >
                    + 예시 추가
                  </Button>
                </div>
                </div>
            </Field>
          </section>

          <section className="rounded-2xl border bg-card">
            <button
              type="button"
              onClick={() => setShowDetails((current) => !current)}
              className="flex w-full items-center justify-between gap-4 p-5 text-left sm:p-6"
            >
              <div>
                <h2 className="font-semibold">
                  상세 설정
                </h2>

                <p className="mt-1 text-sm text-muted-foreground">
                  필요한 경우 구조화된 설정을 직접 작성할 수 있어요.
                </p>
              </div>

              {showDetails ? (
                <ChevronUp className="size-5 shrink-0 text-muted-foreground" />
              ) : (
                <ChevronDown className="size-5 shrink-0 text-muted-foreground" />
              )}
            </button>

            {showDetails && (
              <div className="space-y-5 border-t p-5 sm:p-6">
                <Field label="성격">
                  <Textarea
                    value={personality}
                    onChange={(event) =>
                      setPersonality(event.target.value)
                    }
                    placeholder="성격과 행동 성향"
                    className="min-h-28 resize-y"
                  />
                </Field>

                <Field label="말투">
                  <Textarea
                    value={speechStyle}
                    onChange={(event) =>
                      setSpeechStyle(event.target.value)
                    }
                    placeholder="말투, 어휘, 호칭, 말버릇 등"
                    className="min-h-28 resize-y"
                  />
                </Field>

                <Field label="배경">
                  <Textarea
                    value={background}
                    onChange={(event) =>
                      setBackground(event.target.value)
                    }
                    placeholder="과거사, 소속, 중요한 사건과 관계 등"
                    className="min-h-32 resize-y"
                  />
                </Field>

                <Field label="외모">
                  <Textarea
                    value={appearance}
                    onChange={(event) =>
                      setAppearance(event.target.value)
                    }
                    placeholder="외형적 특징"
                    className="min-h-28 resize-y"
                  />
                </Field>
              </div>
            )}
          </section>

          <section className="space-y-5 rounded-2xl border bg-card p-5 sm:p-6">
            <div>
              <h2 className="font-semibold">3D 모델</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                선택 사항입니다. VRM 모델 경로와 실제 키를 등록하면 Pocket 3D 장면에서 사용할 수 있어요.
              </p>
            </div>
            <Field label="VRM 모델 경로">
              <input
                value={modelUrl}
                onChange={(event) => setModelUrl(event.target.value)}
                maxLength={500}
                placeholder="/models/marbas.vrm"
                className="flex h-11 w-full rounded-md border border-input bg-background px-3 text-sm outline-none transition-colors focus:border-ring"
              />
            </Field>
            <Field label="모델 키 (m)">
              <input
                type="number"
                min="0.1"
                max="3"
                step="0.01"
                value={modelHeight}
                onChange={(event) => setModelHeight(event.target.value)}
                placeholder="2.23"
                className="flex h-11 w-full rounded-md border border-input bg-background px-3 text-sm outline-none transition-colors focus:border-ring"
              />
            </Field>
          </section>

          <section className="rounded-2xl border bg-card p-5 sm:p-6">
            <h2 className="font-semibold">
              월드
            </h2>

            <p className="mt-1 text-sm text-muted-foreground">
              캐릭터는 월드 없이도 개별 대화를 사용할 수 있고, 필요할 때 월드에 참여시킬 수 있어요.
            </p>

            <select value={worldId} onChange={(event) => setWorldId(event.target.value)} className="mt-4 flex h-11 w-full rounded-md border border-input bg-background px-3 text-sm outline-none transition-colors focus:border-ring">
              <option value="">지정하지 않음</option>
              {worlds.map((world) => <option key={world.id} value={world.id}>{world.name}</option>)}
            </select>
          </section>

          {error && (
            <p className="text-sm text-destructive">
              {error}
            </p>
          )}

          <div className="flex justify-end gap-3 pb-10">
            <Button
              type="button"
              variant="outline"
              disabled={saving}
              onClick={onBack}
            >
              취소
            </Button>

            <Button
              type="button"
              disabled={saving || !name.trim()}
              onClick={saveCharacter}
            >
              {saving
                ? (editing ? "저장 중..." : "만드는 중...")
                : (editing ? "변경사항 저장" : "캐릭터 만들기")}
            </Button>
          </div>
        </div>
      </section>
    </main>
  )
}

function Field({
  label,
  description,
  required = false,
  children,
}) {
  return (
    <div>
      <div className="mb-2">
        <label className="text-sm font-medium">
          {label}
          {required && (
            <span className="ml-1 text-destructive">
              *
            </span>
          )}
        </label>

        {description && (
          <p className="mt-1 text-xs leading-5 text-muted-foreground">
            {description}
          </p>
        )}
      </div>

      {children}
    </div>
  )
}
