import { activitySymbol } from "./pocketActivityPlayback.js"
const segmenter = new Intl.Segmenter("ko", { granularity: "grapheme" })
export function topicEmojis(values, fallback) {
  const result = []
  for (const value of Array.isArray(values) ? values : []) {
    if (typeof value !== "string") continue
    for (const { segment } of segmenter.segment(value)) {
      if (/^(?:\p{Extended_Pictographic}|\p{Regional_Indicator}|\p{Emoji_Modifier}|\u200D|\uFE0F|\uFE0E)+$/u.test(segment)) result.push(segment)
      if (result.length === 2) return result
    }
  }
  return result.length ? result : [fallback]
}
export function performanceActors(activity) {
  if (!activity) return []
  return [...(activity.playerInvolved ? ["player"] : []), ...(activity.characterIds ?? []).map(String)]
}
export function actorCue(activity, actorId) {
  const cue = activity?.actorCues?.find(cue => String(cue.actorId) === String(actorId))
  return { emojis: activity?.kind === "SLEEP" ? ["💤"] : topicEmojis(cue?.emojis, activitySymbol(activity?.kind).emoji),
    emotion: ["ANGRY", "HAPPY", "SAD"].includes(cue?.emotion) ? cue.emotion : "CALM" }
}
export function speakingActors(activity) {
  return performanceActors(activity).sort((a,b) =>
    (activity.actorCues?.find(c=>String(c.actorId)===a)?.turn ?? 9) -
    (activity.actorCues?.find(c=>String(c.actorId)===b)?.turn ?? 9))
}
export function walkDirection(activity) {
  const hash = [...(activity?.start ?? "")].reduce((n,c)=>(n*31+c.charCodeAt(0))>>>0,0)
  return [[300,0],[-300,0]][hash % 2]
}
export function puppetPose(activity, actorId, seconds, motion = true) {
  const pose = { x:0, y:0, rx:0, ry:0, rz:0 }
  if (!performanceActors(activity).includes(String(actorId))) return pose
  if (activity.kind === "SLEEP") return { ...pose, y:-0.18, rz:-0.22 }
  const mood = actorCue(activity, actorId).emotion
  if (mood === "SAD") { pose.y=-0.05; pose.rx=0.10; pose.rz=-0.035 }
  if (activity.kind === "WALK" || activity.kind === "TRAVEL") pose.ry=-Math.sign(walkDirection(activity)[0])*Math.PI/6
  if (!motion) return pose
  const t = Number.isFinite(seconds) ? seconds : 0
  if (activity.kind === "WALK" || activity.kind === "TRAVEL") pose.rz=Math.sin(t*7)*0.035
  if (mood === "ANGRY") { pose.x=Math.sin(t*35)*0.009; pose.rz+=Math.sin(t*27)*0.014 }
  if (mood === "HAPPY") pose.y=Math.abs(Math.sin(t*4.2))*0.075
  if (activity.kind === "WALK" || activity.kind === "TRAVEL") { pose.x=0; pose.y=0 }
  return pose
}

// Identical cover scaling for still images and scrolling SVG/image tiles.
export function scrollingBackdrop(width,height,aspect,direction) {
  const ratio=Number.isFinite(aspect) && aspect>0 ? aspect : 1
  const tileWidth=Math.max(width,height*ratio),tileHeight=tileWidth/ratio
  return {width:tileWidth,height:tileHeight,offset:Math.sign(direction)*tileWidth}
}
