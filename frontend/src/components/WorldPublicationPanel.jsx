import { useEffect, useRef, useState } from "react"
import { Button } from "@/components/ui/button"

async function read(response) {
  if (!response.ok) {
    let message = "변경하지 못했어요. 연결과 입력 내용을 확인해 주세요."
    try { const data = await response.json(); message = data.detail || data.message || message } catch { /* empty error body */ }
    throw new Error(message)
  }
  return response.json()
}

export default function WorldPublicationPanel({ world, onClose, onOpenPublished }) {
  const dialog = useRef(null)
  const [settings, setSettings] = useState(null)
  const [tags, setTags] = useState("")
  const [file, setFile] = useState(null)
  const [error, setError] = useState("")
  const [notice, setNotice] = useState("")
  const [busy, setBusy] = useState(false)
  useEffect(() => {
    dialog.current?.showModal()
    const controller = new AbortController()
    fetch(`/api/worlds/${world.id}/publication`, { signal: controller.signal }).then(read).then(data => {
      setSettings(data); setTags(data.hashtags.map(tag => `#${tag}`).join(" "))
    }).catch(e => { if (e.name !== "AbortError") setError(e.message) })
    return () => controller.abort()
  }, [world.id])

  async function save(action) {
    if (busy || !settings) return
    setBusy(true); setError(""); setNotice("")
    try {
      let data = settings
      if (action !== "unpublish") {
        if (file) {
          const form = new FormData(); form.append("cover", file)
          const cover = await fetch(`/api/worlds/${world.id}/cover`, { method: "POST", body: form }).then(read)
          data = { ...data, ...cover }; setSettings(data); setFile(null)
        }
        data = await fetch(`/api/worlds/${world.id}/publication`, {
          method: "PUT", headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ hashtags: tags.split(/[\s,]+/u).filter(Boolean) }),
        }).then(read)
        setSettings(data)
      }
      if (action === "publish" || action === "unpublish") {
        data = await fetch(`/api/worlds/${world.id}/publication`, { method: action === "publish" ? "POST" : "DELETE" }).then(read)
        setSettings(data)
      }
      setNotice(action === "unpublish" ? "배포 목록에서 내렸어요. 기존 포켓은 계속 이용할 수 있어요." : action === "publish" ? "배포했어요. 배포 메뉴에서 확인할 수 있어요." : "표지와 해시태그를 저장했어요.")
    } catch (e) { setError(e.message) }
    finally { setBusy(false) }
  }

  return <dialog ref={dialog} className="publication-dialog" onCancel={event => { event.preventDefault(); if (!busy) onClose() }} aria-labelledby="publication-heading">
    <div className="publication-dialog-heading"><h2 id="publication-heading">월드 배포</h2><Button variant="ghost" disabled={busy} onClick={onClose}>닫기</Button></div>
    <p className="publication-help">{world.name}의 표지와 해시태그를 준비하고 다른 이용자에게 공개하세요.</p>
    {error && <p role="alert" className="publication-error">{error}</p>}
    {notice && <p role="status" className="publication-help">{notice}</p>}
    {!settings ? <p>배포 정보를 불러오는 중…</p> : <>
      <div className="publication-cover-setting">
        {settings.coverImageUrl ? <img src={settings.coverImageUrl} alt={`${world.name} 표지`} /> : <div className="publication-cover-empty">월드 표지</div>}
        <label>표지 이미지<input disabled={busy} type="file" accept="image/png,image/jpeg" onChange={e => {
          const image = e.target.files?.[0] ?? null
          if (image && (!["image/png", "image/jpeg"].includes(image.type) || image.size > 3 * 1024 * 1024)) { setError("PNG/JPEG 3MB 이하로 선택해 주세요."); e.target.value = ""; setFile(null); return }
          setFile(image); setError("")
        }} /><small>PNG/JPEG · 최대 3MB · 가로·세로 최대 4096px{file && ` · 선택: ${file.name}`}</small></label>
      </div>
      <label className="publication-field">해시태그<input value={tags} maxLength={330} disabled={busy} onChange={e => setTags(e.target.value)} placeholder="#판타지 #일상 #미스터리" /><small>공백으로 구분 · 최대 10개 · 태그당 30자</small></label>
      <p className="publication-help">배포하면 현재 월드의 플레이가이드와 모든 인트로가 공개됩니다. 이후 제작 화면에서 수정한 내용도 반영됩니다. 이용자는 인트로를 수정할 수 없으며 새 포켓을 만들어 플레이합니다.</p>
      <div className="publication-actions">
        <Button variant="secondary" disabled={busy} onClick={() => save("save")}>저장</Button>
        {settings.publishedAt ? <><Button variant="outline" disabled={busy} onClick={() => save("unpublish")}>배포 내리기</Button><Button disabled={busy} onClick={() => onOpenPublished(world.id)}>배포 화면 보기</Button></> : <Button disabled={busy} onClick={() => save("publish")}>{busy ? "처리 중…" : "배포하기"}</Button>}
      </div>
    </>}
  </dialog>
}
