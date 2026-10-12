import { useEffect, useMemo, useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import PocketAbsenceDialog from "@/components/PocketAbsenceDialog"
import { actorCue } from "@/lib/pocketActivityPerformance"
import PocketScene from "@/components/PocketScene"
import { ACTIVITY_FRAME_MS, activitySymbol, activityParticipants, activityModels, nextActivityIndex, replayIncludesPlayer } from "@/lib/pocketActivityPlayback"

export default function PocketActivityScene({ sessionId, report, freshReportId, characters, sceneModels,
  simulatedAt, location, disabled, onDiscard, progressError }) {
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
    if (disabled) setPlaying(false)
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
    setSelection({ sessionId, reportId: report.id, index: 0 })
    setPlaying(!window.matchMedia("(prefers-reduced-motion: reduce)").matches)
  }, [sessionId, report?.id, freshReportId, frames.length, disabled])
  useEffect(() => {
    if (!playing || !activity || disabled || !visible) return
    const timer = setTimeout(() => {
      const next = nextActivityIndex(index, frames.length)
      if (next != null) setSelection({ sessionId, reportId: report.id, index: next })
      if (next == null) setPlaying(false)
    }, ACTIVITY_FRAME_MS)
    return () => clearTimeout(timer)
  }, [playing, index, activity, frames.length, report?.id, sessionId, disabled, visible])

  const activityDisplay = activity && (replayReady ? <PocketScene compact sessionId={sessionId}
    simulatedAt={activity.start} location={activity.location} playerModel={replayPlayer}
    characterModels={replayModels} activity={activity} />
    : <div className="pocket-activity-fallback pocket-activity-fallback--device">
        {(activity.playerInvolved || replayPlayer) && <div className="pocket-activity-person">
          <span>{sceneModels.playerModel?.source?.name || "플레이어"}</span>
          {activity.playerInvolved && <span className="pocket-activity-icon">{actorCue(activity,"player").emojis.join("")}</span>}
        </div>}
        {participants.map(character => <div key={character.id ?? character.characterId} className="pocket-activity-person">
          <span className="pocket-activity-icon" aria-label={symbol.label}>{actorCue(activity,character.id ?? character.characterId).emojis.join("")}</span>
          <span>{character.name ?? character.characterName}</span>
        </div>)}
      </div>)
  return <div>
    {frames.length > 0 && <section aria-label="부재 활동 재생" className="mt-2 flex items-center justify-between gap-2 text-xs">
      <span className="text-muted-foreground">지난 부재 활동 · {frames.length}개</span>
      <Button size="sm" variant="outline" disabled={disabled} onClick={() => select(0, true)}>다시보기</Button>
    </section>}
    <PocketAbsenceDialog open={Boolean(activity)} onClose={() => select(null)} display={activityDisplay}
      activity={activity} index={index ?? 0} count={frames.length} playing={playing} disabled={disabled}
      onToggle={() => setPlaying(value => !value)} onSelect={select}
      onReplay={() => select(0, !window.matchMedia("(prefers-reduced-motion: reduce)").matches)}
      error={progressError} onDiscard={async () => {
        const removed = await onDiscard?.(report.id)
        if (removed) select(null)
      }} />
  </div>
}
