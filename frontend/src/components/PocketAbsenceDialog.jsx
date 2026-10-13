import { useEffect, useRef } from "react"
import { createPortal } from "react-dom"
import { Button } from "@/components/ui/button"
import { activitySymbol } from "@/lib/pocketActivityPlayback"
import "./PocketAbsenceDialog.css"

export default function PocketAbsenceDialog({ open, onClose, display, activity, index, count, playing,
  disabled, waiting = false, onToggle, onSelect, onReplay, onDiscard, error }) {
  const dialog = useRef(null)
  useEffect(() => {
    if (!open) return
    const element = dialog.current
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = "hidden"
    element.showModal()
    return () => { element.close(); document.body.style.overflow = previousOverflow }
  }, [open])
  if (!open || !activity) return null
  const symbol = activitySymbol(activity.kind)
  return createPortal(<dialog ref={dialog} className="pocket-absence-dialog"
    aria-labelledby="pocket-absence-title" onCancel={event => { event.preventDefault(); onClose() }}>
    <header className="pocket-absence-dialog__header">
      <div><p className="pocket-absence-dialog__eyebrow">POCKET LIFE</p>
        <h2 id="pocket-absence-title">부재 중의 작은 일상</h2></div>
      <Button variant="ghost" size="icon" aria-label="다마고치 화면 닫기" autoFocus onClick={onClose}>×</Button>
    </header>
    <div className="pocket-absence-device" aria-label="부재 활동 디스플레이">{display}</div>
    {!waiting && <div className="pocket-absence-dialog__caption" aria-live="polite">
      <p>{symbol.label}<span>{activity.location || "장소 미설정"}</span></p>
    </div>}
    <div className="pocket-absence-dialog__playback">
      <Button variant="ghost" size="sm" disabled={disabled || waiting || index === 0} aria-label="이전 활동" onClick={() => onSelect(index - 1)}>‹</Button>
      <span>{index + 1} / {count}</span>
      <Button variant="ghost" size="sm" disabled={disabled || waiting} onClick={onToggle}>{waiting ? "준비 중" : playing ? "일시정지" : "재생"}</Button>
      <Button variant="ghost" size="sm" disabled={disabled || waiting || index === count - 1} aria-label="다음 활동" onClick={() => onSelect(index + 1)}>›</Button>
    </div>
    {error && <p role="alert" className="pocket-absence-dialog__error">{error}</p>}
    <div className="pocket-absence-dialog__actions">
      <Button variant="outline" disabled title="자세한 이야기 기능은 준비 중이에요">자세히 보기</Button>
      <Button disabled={disabled || waiting} onClick={onReplay}>다시보기</Button>
      <Button variant="ghost" className="pocket-absence-dialog__discard" disabled={disabled} onClick={onDiscard}>모르는 일이에요</Button>
    </div>
  </dialog>, document.body)
}
