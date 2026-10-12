import { useEffect, useMemo, useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import PocketScene from "@/components/PocketScene"
import { ACTIVITY_FRAME_MS, activitySymbol, activityParticipants, activityModels, nextActivityIndex, replayIncludesPlayer } from "@/lib/pocketActivityPlayback"

export default function PocketActivityScene({ sessionId, report, freshReportId, characters, sceneModels,
  simulatedAt, location, disabled }) {
  const [selection, setSelection] = useState(null)
  const [playing, setPlaying] = useState(false)
  const [visible, setVisible] = useState(!document.hidden)
  const consumed = useRef(null)
  const frames = report?.activities ?? []
  const index = selection?.sessionId === sessionId && selection?.reportId === report?.id ? selection.index : null
  const activity = index != null ? frames[index] ?? null : null
  const participantKey = (activity?.characterIds ?? []).join(",")
  const participants = useMemo(() => activityParticipants({
    characterIds: participantKey.split(",").filter(Boolean).map(Number),
  }, characters), [participantKey, characters])
  // Place/time changes keep identical actor models mounted.
  const generatedReplayModels = useMemo(() => activityModels(participants), [participants])
  const sameCast = participants.length === sceneModels.castModels.length && participants.every(character =>
    sceneModels.castModels.some(model => (model.source.id ?? model.source.characterId) === (character.id ?? character.characterId)))
  const replayAtCurrentPlace = Boolean(activity && replayIncludesPlayer(location, activity.location))
  const replayModels = useMemo(() => {
    // Explicit player activity shows its actual companions, not every Pocket member.
    if (activity?.playerInvolved || !replayAtCurrentPlace) return sameCast ? sceneModels.castModels : generatedReplayModels
    const presentIds = new Set(sceneModels.castModels.map(model => model.source.id ?? model.source.characterId))
    const additional = generatedReplayModels.filter(model => !presentIds.has(model.source.id ?? model.source.characterId))
    return additional.length ? [...sceneModels.castModels, ...additional] : sceneModels.castModels
  }, [activity?.playerInvolved, replayAtCurrentPlace, sameCast, sceneModels.castModels, generatedReplayModels])
  const replayPlayer = (activity?.playerInvolved || replayAtCurrentPlace) ? sceneModels.playerModel : null
  const replayReady = (participants.length > 0 || (activity?.playerInvolved && replayPlayer)) && generatedReplayModels.length === participants.length
  const symbol = activitySymbol(activity?.kind)
  function select(next, shouldPlay = false) {
    setSelection(next == null ? null : { sessionId, reportId: report.id, index: next })
    setPlaying(next != null && shouldPlay)
  }
  useEffect(() => {
    if (disabled) { setSelection(null); setPlaying(false) }
  }, [disabled])
  useEffect(() => {
    const visibility = () => setVisible(!document.hidden)
    document.addEventListener("visibilitychange", visibility)
    return () => document.removeEventListener("visibilitychange", visibility)
  }, [])
  useEffect(() => {
    if (!report || freshReportId !== report.id || !frames.length || disabled) return
    const key = `${sessionId}:${report.id}`
    if (consumed.current === key) return
    consumed.current = key
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return
    setSelection({ sessionId, reportId: report.id, index: 0 }); setPlaying(true)
  }, [sessionId, report?.id, freshReportId, frames.length, disabled])
  useEffect(() => {
    if (!playing || !activity || disabled || !visible) return
    const timer = setTimeout(() => {
      const next = nextActivityIndex(index, frames.length)
      setSelection(next == null ? null : { sessionId, reportId: report.id, index: next })
      if (next == null) setPlaying(false)
    }, ACTIVITY_FRAME_MS)
    return () => clearTimeout(timer)
  }, [playing, index, activity, frames.length, report?.id, sessionId, disabled, visible])

  return <div>
    {activity ? <>
      {replayReady ? <PocketScene sessionId={sessionId} simulatedAt={activity.start}
        location={activity.location} playerModel={replayPlayer} characterModels={replayModels} activity={activity} />
        : <div className="pocket-activity-fallback" aria-label="모델 미등록 캐릭터의 활동 미리보기">
            {replayPlayer && <div className="pocket-activity-person"><span>{replayPlayer.source.name}</span>{activity?.playerInvolved && <span className="pocket-activity-icon" aria-label={symbol.label}>{symbol.emoji}</span>}</div>}
            {participants.map(character => <div key={character.id ?? character.characterId} className="pocket-activity-person">
              <span className="pocket-activity-icon" aria-label={symbol.label}>{symbol.emoji}</span>
              <span>{character.name ?? character.characterName}</span>
            </div>)}
            {!participants.length && !activity?.playerInvolved && <span>참여 캐릭터 정보를 불러오는 중…</span>}
          </div>}
    </> : sceneModels.ready ? <PocketScene sessionId={sessionId} simulatedAt={simulatedAt} location={location}
      playerModel={sceneModels.playerModel} characterModels={sceneModels.castModels} />
      : <div className="flex min-h-24 items-center justify-center rounded-md border border-dashed px-4 text-center text-xs text-muted-foreground">
          3D 장면은 선택한 플레이어 프로필과 이 Pocket의 모든 캐릭터에 모델이 등록된 경우에만 사용할 수 있어요.
        </div>}
    {frames.length > 0 && <section aria-label="부재 활동 재생" className="mt-2 flex flex-wrap items-center gap-2 text-xs">
      <span role="status" className="mr-auto text-muted-foreground">{activity
        ? `부재 활동 ${index + 1} / ${frames.length} · ${symbol.label} · ${activity.location || "장소 미설정"}`
        : `지난 부재 활동 · ${frames.length}개`}</span>
      {activity ? <>
        <Button size="sm" variant="outline" disabled={disabled || index === 0}
          onClick={() => select(index - 1)} aria-label="이전 활동">이전</Button>
        <Button size="sm" variant="outline" disabled={disabled} onClick={() => setPlaying(value => !value)}>
          {playing ? "일시정지" : "재생"}</Button>
        <Button size="sm" variant="outline" disabled={disabled || index >= frames.length - 1}
          onClick={() => select(index + 1)} aria-label="다음 활동">다음</Button>
        <Button size="sm" variant="ghost" onClick={() => select(null)}>현재 장면</Button>
      </> : <Button size="sm" variant="outline" disabled={disabled} onClick={() => select(0, true)}>활동 다시보기</Button>}
    </section>}
  </div>
}
