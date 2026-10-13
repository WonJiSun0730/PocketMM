import { useState } from "react"
import { Button } from "@/components/ui/button"
import usePocketBackground from "@/hooks/usePocketBackground"

export default function PocketBackgroundPanel({ sessionId, location = "" }) {
  const [place, setPlace] = useState("")
  const [error, setError] = useState("")
  const [saving, setSaving] = useState(false)
  const target = place.trim() || location || ""
  const scene = usePocketBackground(sessionId, target)
  const busy = saving || scene.status === "GENERATING"
  async function run(action) {
    setSaving(true); setError("")
    try { await action() } catch (error) { setError(error.message) } finally { setSaving(false) }
  }
  return <section className="grid gap-3 rounded-lg border bg-card p-3.5" aria-label="장소 배경">
    <div><h3 className="text-sm font-medium">장소 배경</h3>
      <p className="mt-1 text-xs leading-5 text-muted-foreground">저장된 비슷한 장소를 우선 사용해요. 새 장소만 배경과 투명 원경을 만들어 서버에 저장해요.</p></div>
    <label className="grid gap-1.5 text-xs">장소
      <input className="h-9 rounded-md border bg-background px-3 text-sm" maxLength={200}
        value={place} placeholder={location || "배경을 만들 장소를 입력해 주세요"} onChange={e => setPlace(e.target.value)} />
    </label>
    <div className="flex items-center justify-between gap-3">
      <div><p className="text-xs font-medium">새 장소 자동 생성</p>
        <p className="mt-1 text-xs text-muted-foreground">기본 모델 신규 두 장 출력 약 $0.0672 · 입력 비용 별도 · 재사용은 생성비 없음</p></div>
      <button type="button" role="switch" aria-label="새 장소 자동 생성" aria-checked={scene.automatic}
        disabled={saving || !target || !scene.available}
        onClick={() => run(() => scene.configure(!scene.automatic))}
        className={`relative h-6 w-11 shrink-0 rounded-full border transition-colors disabled:opacity-50 ${scene.automatic ? "border-primary bg-primary" : "border-input bg-muted"}`}>
        <span className={`absolute left-0 top-0.5 size-4 rounded-full bg-background shadow-sm transition-transform ${scene.automatic ? "translate-x-6" : "translate-x-1"}`} />
      </button>
    </div>
    {scene.asset && <div className="grid grid-cols-2 gap-2" aria-label="저장된 배경 미리보기">
      <img className="aspect-[3/2] w-full rounded-md border [image-rendering:pixelated]" src={scene.asset.backgroundUrl} alt="저장된 배경" />
      <img className="aspect-[3/2] w-full rounded-md border bg-muted [image-rendering:pixelated]" src={scene.asset.sceneryUrl} alt="투명 원경" />
    </div>}
    <p className="text-xs text-muted-foreground" role="status">{scene.status === "GENERATING" ? "배경을 만들고 있어요. 기본 배경으로 계속 재생할 수 있어요."
      : scene.asset ? `${scene.reused ? "비슷한 장소의 저장 이미지를 사용해요" : "저장된 이미지를 사용해요"} · ${scene.asset.location}`
      : scene.status === "FAILED" ? "배경을 불러오지 못했어요. 기본 배경을 유지해요."
      : scene.available ? "저장 이미지가 없으면 기본 배경을 사용해요." : "서버의 이미지 생성 설정이 필요해요. 직접 첨부는 가능해요."}</p>
    <Button type="button" size="sm" variant="outline" disabled={busy || !target || (!scene.available && scene.status !== "FAILED")}
      onClick={() => run(scene.available ? scene.generate : scene.reload)}>{scene.status === "FAILED"
        ? scene.available ? "생성 다시 시도" : "배경 다시 확인" : "저장 배경 찾기 / 새로 만들기"}</Button>
    <details className="rounded-md border p-3 text-xs">
      <summary className="cursor-pointer font-medium">배경 직접 첨부</summary>
      <p className="my-2 leading-5 text-muted-foreground">3:2 이미지 두 장을 준비해 주세요. 원경의 빈 공간은 투명 PNG여야 해요. 각 파일 10MB 이하.</p>
      <form className="grid gap-3" onSubmit={event => {
        event.preventDefault(); const form = new FormData(event.currentTarget); form.set("location", target)
        run(() => scene.upload(form))
      }}>
        <label>재사용할 다른 장소 이름 / 키워드<input name="keywords" maxLength={500} placeholder="쉼표로 구분" className="mt-1 h-9 w-full rounded-md border bg-background px-3" /></label>
        <label>배경<input name="background" type="file" accept="image/png,image/jpeg" required className="mt-1 block w-full" /></label>
        <label>투명 원경<input name="scenery" type="file" accept="image/png" required className="mt-1 block w-full" /></label>
        <Button type="submit" variant="outline" size="sm" disabled={busy || !target}>첨부 이미지 저장</Button>
      </form>
    </details>
    {(error || scene.error) && <p role="alert" className="text-xs text-destructive">{error || scene.error}</p>}
  </section>
}
