import { useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"

async function readResponse(response) {
  if (!response.ok) {
    if (response.status === 409) throw new Error("최대 10개 제한 또는 목록 변경을 확인해 주세요.")
    throw new Error("저장하지 못했습니다. 입력 내용과 연결을 확인해 주세요.")
  }
  return response.json()
}

export default function PlayerProfilePicker({ current, busy, onSelect, onCancel, onSaved, manageOnly = false }) {
  const [profiles, setProfiles] = useState([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")
  const [draft, setDraft] = useState(null)

  useEffect(() => {
    let active = true
    fetch("/api/player-profiles").then(readResponse).then(data => {
      if (active) setProfiles(data)
    }).catch(e => { if (active) setError(e.message) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])

  async function save(event) {
    event.preventDefault()
    if (saving || busy) return
    setSaving(true)
    setError("")
    try {
      const profile = await fetch(`/api/player-profiles${draft.id ? `/${draft.id}` : ""}`, {
        method: draft.id ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: draft.name, description: draft.description, avatar: draft.avatar || null, modelUrl: draft.modelUrl?.trim() || null, modelHeight: draft.modelUrl?.trim() && draft.modelHeight ? Number(draft.modelHeight) : null }),
      }).then(readResponse)
      setProfiles(items => draft.id ? items.map(p => p.id === profile.id ? profile : p) : [...items, profile])
      onSaved?.(profile)
      setDraft(null)
    } catch (e) { setError(e.message) }
    finally { setSaving(false) }
  }

  async function readImage(event) {
    const file = event.target.files?.[0]
    if (!file) return
    if (!["image/png", "image/jpeg", "image/webp"].includes(file.type) || file.size > 280000) {
      setError("PNG/JPEG/WebP 파일을 280KB 이하로 선택해 주세요.")
      return
    }
    // 사진은 화면 표시용이다. 대화 모델에는 이름·설정 텍스트만 전달한다.
    const reader = new FileReader()
    reader.onload = () => setDraft(value => value ? { ...value, avatar: reader.result } : value)
    reader.onerror = () => setError("이미지를 읽지 못했습니다.")
    reader.readAsDataURL(file)
  }

  const disabled = busy || saving || loading
  return (
    <section aria-label="유저 캐릭터 프로필 선택" className="mx-auto w-full max-w-2xl space-y-4 p-4">
      <h2 className="text-lg font-semibold">{manageOnly ? "플레이어 프로필 관리" : "어떤 캐릭터로 대화할까요?"}</h2>
      <p className="text-sm text-muted-foreground">이름·설정 합계 500자 · 최대 10개 · 사진은 표시용</p>
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
      {loading && <p>프로필을 불러오는 중...</p>}
      {!draft && <>
        {profiles.map(profile => (
          <div key={profile.id} className="flex items-center gap-3 rounded-lg border p-3">
            {profile.avatar && <img src={profile.avatar} alt="" className="size-12 shrink-0 rounded-full object-cover" />}
            <div className="min-w-0 flex-1">
              <p className="font-medium">{profile.name}{current?.id === profile.id ? " · 현재 선택" : ""}</p>
              <p className="line-clamp-3 whitespace-pre-wrap break-words text-sm text-muted-foreground">{profile.description}</p>
            </div>
            <Button variant="outline" disabled={disabled} onClick={() => setDraft({ ...profile })}>수정</Button>
            {!manageOnly && <Button disabled={disabled} onClick={() => onSelect(profile)}>선택</Button>}
          </div>
        ))}
        {!loading && profiles.length === 0 && <p>아직 프로필이 없어요. 먼저 만들어 주세요.</p>}
        <div className="flex gap-2">
          <Button disabled={disabled || profiles.length >= 10} onClick={() => setDraft({ name: "", description: "", avatar: null, modelUrl: "", modelHeight: "" })}>프로필 만들기 ({profiles.length}/10)</Button>
          {onCancel && <Button variant="ghost" disabled={busy || saving} onClick={onCancel}>돌아가기</Button>}
        </div>
      </>}
      {draft && <form onSubmit={save} className="space-y-3">
        <label className="block text-sm">이름
          <input required maxLength={40} value={draft.name} disabled={disabled}
            onChange={e => setDraft({ ...draft, name: e.target.value })}
            className="mt-1 w-full rounded-md border bg-background p-2" />
        </label>
        <label className="block text-sm">캐릭터 설정
          <Textarea required maxLength={500 - draft.name.length} value={draft.description} disabled={disabled}
            onChange={e => setDraft({ ...draft, description: e.target.value })}
            placeholder="외형, 성격, 배경 등 대화에 반영할 내용을 적어 주세요." className="mt-1 min-h-32" />
        </label>
        <p className="text-sm text-muted-foreground">{draft.name.length + draft.description.length}/500자</p>
        <label className="block text-sm">프로필 사진 (선택)
          <input type="file" accept="image/png,image/jpeg,image/webp" disabled={disabled} onChange={readImage} className="mt-1 block w-full" />
        </label>
        {draft.avatar && <div className="flex items-center gap-2">
          <img src={draft.avatar} alt="프로필 미리보기" className="size-16 rounded-full object-cover" />
          <Button type="button" variant="ghost" disabled={disabled} onClick={() => setDraft({ ...draft, avatar: null })}>사진 제거</Button>
        </div>}
        <label className="block text-sm">3D VRM 모델 경로 (선택)
          <input maxLength={500} value={draft.modelUrl ?? ""} disabled={disabled}
            onChange={e => setDraft({ ...draft, modelUrl: e.target.value })}
            placeholder="/models/milieponygood.vrm"
            className="mt-1 w-full rounded-md border bg-background p-2" />
        </label>
        <label className="block text-sm">3D 모델 키 (m)
          <input type="number" min="0.1" max="3" step="0.01" value={draft.modelHeight ?? ""} disabled={disabled}
            onChange={e => setDraft({ ...draft, modelHeight: e.target.value })}
            placeholder="1.60"
            className="mt-1 w-full rounded-md border bg-background p-2" />
        </label>
        {draft.id && <p className="text-sm text-muted-foreground">수정한 설정은 이 프로필을 사용하는 다른 대화에도 반영됩니다.</p>}
        <div className="flex gap-2">
          <Button type="submit" disabled={disabled || !draft.name.trim() || !draft.description.trim() || draft.name.length + draft.description.length > 500}>{saving ? "저장 중..." : "저장"}</Button>
          <Button type="button" variant="outline" disabled={disabled} onClick={() => setDraft(null)}>취소</Button>
        </div>
      </form>}
    </section>
  )
}
