import { useEffect, useMemo, useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import PocketAbsenceDialog from "@/components/PocketAbsenceDialog"
import { actorCue } from "@/lib/pocketActivityPerformance"
import PocketScene from "@/components/PocketScene"
import usePocketBackground from "@/hooks/usePocketBackground"
import usePocketSceneImages from "@/hooks/usePocketSceneImages"
import { scenePreparationState } from "@/lib/pocketScenePreparation"
import usePocketSceneLook from "@/hooks/usePocketSceneLook"
import { lookFilter } from "@/lib/pocketSceneLook"
import { ACTIVITY_FRAME_MS, activitySymbol, activityParticipants, activityModels, nextActivityIndex, replayIncludesPlayer } from "@/lib/pocketActivityPlayback"

export default function PocketActivityScene({ sessionId, report, freshReportId, characters, sceneModels,
  simulatedAt, location, disabled, onDiscard, progressError, isDeveloper = false }) {
  const [look] = usePocketSceneLook()
  const [selection, setSelection] = useState(null)
  const [playing, setPlaying] = useState(false)
  const [visible, setVisible] = useState(!document.hidden)
  const consumed = useRef(null)
  const frames = report?.activities ?? []
  const index = selection?.sessionId === sessionId && selection?.reportId === report?.id ? selection.index : null
  const activity = index != null ? frames[index] ?? null : null
  const background = usePocketBackground(sessionId, activity && sceneModels.ready ? activity.location || location || "장소 미설정" : "", { simulatedAt:activity?.start })
  const images = usePocketSceneImages(activity ? background.asset : null)
  const [loadedModelKey, setLoadedModelKey] = useState(null)
  const [renderError, setRenderError] = useState("")
  const [renderRevision, setRenderRevision] = useState(0)
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
  const modelKey = `${background.asset?.id}:${replayPlayer?.url ?? ""}:${replayModels.map(model=>`${model.url}:${model.height}`).join("|")}:${renderRevision}`
  const preparation = renderError ? "ERROR" : scenePreparationState(background, images, !replayReady || loadedModelKey === modelKey)
  const waiting = preparation !== "READY"
  useEffect(() => {
    if (!activity || !images.ready) setLoadedModelKey(null)
    setRenderError("")
  }, [activity?.location, images.ready, Boolean(activity)])
  const symbol = activitySymbol(activity?.kind)
  function select(next, shouldPlay = false) {
    setSelection(next == null ? null : { sessionId, reportId: report.id, index: next })
    setPlaying(next != null && shouldPlay)
  }
  useEffect(() => {
    if (disabled) setPlaying(false)
  }, [disabled])
  useEffect(()=>{if(!sceneModels.ready) {setSelection(null);setPlaying(false)}},[sceneModels.ready])
  useEffect(() => {
    const visibility = () => setVisible(!document.hidden)
    document.addEventListener("visibilitychange", visibility)
    return () => document.removeEventListener("visibilitychange", visibility)
  }, [])
  useEffect(() => {
    if (!report || freshReportId !== report.id || !frames.length || disabled || !sceneModels.ready) return
    const key = `${sessionId}:${report.id}`
    if (consumed.current === key) return
    consumed.current = key
    setSelection({ sessionId, reportId: report.id, index: 0 })
    setPlaying(!window.matchMedia("(prefers-reduced-motion: reduce)").matches)
  }, [sessionId, report?.id, freshReportId, frames.length, disabled,sceneModels.ready])
  useEffect(() => {
    if (!playing || !activity || disabled || !visible || waiting) return
    const timer = setTimeout(() => {
      const next = nextActivityIndex(index, frames.length)
      if (next != null) setSelection({ sessionId, reportId: report.id, index: next })
      if (next == null) setPlaying(false)
    }, ACTIVITY_FRAME_MS)
    return () => clearTimeout(timer)
  }, [playing, index, activity, frames.length, report?.id, sessionId, disabled, visible, waiting])

  const preparedDisplay = activity && background.asset && images.ready && (replayReady ? <PocketScene compact sessionId={sessionId}
    key={renderRevision} requireBackground backgroundAsset={background.asset}
    onModelsReady={ready=>setLoadedModelKey(ready ? modelKey : null)} onSceneError={setRenderError}
    simulatedAt={activity.start} location={activity.location} playerModel={replayPlayer}
    characterModels={replayModels} activity={activity} />
    : <div className="pocket-activity-fallback pocket-activity-fallback--device" style={{backgroundImage:`url(${background.asset.backgroundUrl})`, filter:lookFilter(look)}}>
        <img className="pocket-activity-fallback__scenery" src={background.asset.sceneryUrl} alt="" />
        {(activity.playerInvolved || replayPlayer) && <div className="pocket-activity-person">
          <span>{sceneModels.playerModel?.source?.name || "플레이어"}</span>
          {activity.playerInvolved && <span className="pocket-activity-icon">{actorCue(activity,"player").emojis.join("")}</span>}
        </div>}
        {participants.map(character => <div key={character.id ?? character.characterId} className="pocket-activity-person">
          <span className="pocket-activity-icon" aria-label={symbol.label}>{actorCue(activity,character.id ?? character.characterId).emojis.join("")}</span>
          <span>{character.name ?? character.characterName}</span>
        </div>)}
      </div>)
  const activityDisplay = activity && <div className="pocket-prepared-display" data-scene-ready={!waiting}>
    {preparedDisplay && <div className="pocket-prepared-display__scene" style={{visibility:waiting ? "hidden" : "visible"}}>{preparedDisplay}</div>}
    {waiting && <div className="pocket-scene-loading" role="status" aria-live="polite">
      <span className="pocket-scene-loading__dots" aria-hidden="true"><i/><i/><i/></span>
      <p>{preparation === "ERROR" ? "주머니 안쪽이 아직 잘 보이지 않아요." : "주머니 안쪽을 들여다보고 있어요..."}</p>
      {preparation === "ERROR" && <details className="pocket-scene-loading__details"><summary>문제 확인</summary>
        <p>{renderError || images.error || background.error || (!background.automatic
          ? "Pocket 설정에서 새 장소 자동 생성을 켜거나 배경을 첨부해 주세요."
          : !background.available ? "이미지 생성 연결 설정을 확인해 주세요." : "장면 준비를 다시 시도해 주세요.")}</p>
      </details>}
      {preparation === "ERROR" && <Button variant="outline" size="sm" onClick={async()=>{
        setRenderError(""); setRenderRevision(value=>value+1)
        if (background.asset) { images.retry(); background.reload() }
        else { try { if (background.available) await background.generate(); else background.reload() } catch (error) { setRenderError(error.message || "주머니 안쪽을 준비하지 못했어요.") } }
      }}>다시 들여다보기</Button>}
    </div>}
  </div>
  return <div>
    {frames.length > 0 && <section aria-label="부재 활동 재생" className="mt-2 flex items-center justify-between gap-2 text-xs">
      <span className="text-muted-foreground">지난 부재 활동 · {frames.length}개</span>
      <Button size="sm" variant="outline" disabled={disabled || !sceneModels.ready} onClick={() => select(0, true)}>다시보기</Button>
    </section>}
    <PocketAbsenceDialog sessionId={sessionId} report={report} isDeveloper={isDeveloper} open={Boolean(activity) && sceneModels.ready} onClose={() => select(null)} display={activityDisplay}
      activity={activity} index={index ?? 0} count={frames.length} playing={playing} disabled={disabled} waiting={waiting}
      onToggle={() => setPlaying(value => !value)} onSelect={select}
      onReplay={() => select(0, !window.matchMedia("(prefers-reduced-motion: reduce)").matches)}
      error={progressError} onDiscard={async () => {
        const removed = await onDiscard?.(report.id)
        if (removed) select(null)
      }} />
  </div>
}
