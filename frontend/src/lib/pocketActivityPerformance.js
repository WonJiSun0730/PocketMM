import { activitySymbol } from "./pocketActivityPlayback.js"
const segmenter = new Intl.Segmenter("ko", { granularity: "grapheme" })
const foodEmojis = new Set([...segmenter.segment("🍏🍎🍐🍊🍋🍌🍉🍇🍓🫐🍈🍒🍑🥭🍍🥥🥝🍅🍆🥑🥦🥬🥒🌶️🫑🌽🥕🫒🧄🧅🥔🍠🥐🥯🍞🥖🥨🧀🥚🍳🧈🥞🧇🥓🥩🍗🍖🌭🍔🍟🍕🫓🥪🥙🧆🌮🌯🫔🥗🥘🫕🥫🍝🍜🍲🍛🍣🍱🥟🦪🍤🍙🍚🍘🍥🥠🥮🍢🍡🍧🍨🍦🥧🧁🍰🎂🍮🍭🍬🍫🍿🍩🍪🍽️")].map(x=>x.segment.replace(/\uFE0F/g,"")))
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
export function interactionActors(activity) {
  if(!["CLOSENESS","AFFECTION","INTIMACY"].includes(activity?.interaction)) return []
  const allowed=performanceActors(activity), ids=activity.interactionActorIds ?? []
  const pair=ids.length ? [...new Set(ids.map(String))].filter(id=>allowed.includes(id)) : allowed
  return pair.length===2 ? pair : []
}
export function actorCue(activity, actorId) {
  const cue = activity?.actorCues?.find(cue => String(cue.actorId) === String(actorId))
  const topics=topicEmojis(cue?.emojis, activitySymbol(activity?.kind).emoji)
  const meal=topics.find(e=>foodEmojis.has(e.replace(/\uFE0F/g,""))) || "🍽️"
  return { emojis: activity?.kind === "SLEEP" ? ["💤"] : activity?.kind === "MEAL" ? [meal] : topics,
    emotion: ["ANGRY", "HAPPY", "SAD"].includes(cue?.emotion) ? cue.emotion : "CALM",
    pose: cue?.pose === "ARMS_CROSSED" ? "ARMS_CROSSED" : "LIGHT" }
}
export function speakingActors(activity) {
  return performanceActors(activity).sort((a,b) =>
    (activity.actorCues?.find(c=>String(c.actorId)===a)?.turn ?? 9) -
    (activity.actorCues?.find(c=>String(c.actorId)===b)?.turn ?? 9))
}
export function speechSide(yaw, frontalSide) {
  const x=Math.sin(Number.isFinite(yaw) ? yaw : 0)
  return Math.abs(x)>.08 ? Math.sign(x) : frontalSide < 0 ? -1 : 1
}
export function walkDirection(activity) {
  const hash = [...(activity?.start ?? "")].reduce((n,c)=>(n*31+c.charCodeAt(0))>>>0,0)
  return [[300,0],[-300,0]][hash % 2]
}
export function puppetPose(activity, actorId, seconds, motion = true) {
  const pose = { x:0, y:0, rx:0, ry:0, rz:0, armRaise:0, stride:0 }
  if (!performanceActors(activity).includes(String(actorId))) return pose
  if (activity.kind === "SLEEP" || (activity.interaction === "INTIMACY" && interactionActors(activity).includes(String(actorId)))) {
    const progress=motion ? Math.max(0,Math.min(1,(Number.isFinite(seconds)?seconds:0)/1.2)) : 1
    return {...pose,y:-.32*progress,rz:-Math.PI/2*progress,hidden:progress>=1}
  }
  const mood = actorCue(activity, actorId).emotion
  if (mood === "SAD") { pose.y=-0.05; pose.rx=0.10; pose.rz=-0.035 }
  if (["WALK","TRAVEL","RUN"].includes(activity.kind)) pose.ry=-Math.sign(walkDirection(activity)[0])*Math.PI/6
  if (!motion) return pose
  const t = Number.isFinite(seconds) ? seconds : 0
  if (activity.kind === "MEAL" || activity.kind === "COFFEE" || activity.kind === "DRINK") {
    pose.armRaise=(Math.sin(t*2.6)+1)*.48;pose.rx=Math.sin(t*2.6)*.025
  }
  if (activity.kind === "RUN") {pose.rz=Math.sin(t*12)*.045;pose.y=Math.abs(Math.sin(t*6))*.035;pose.stride=Math.sin(t*12)*.4;pose.armRaise=.25}
  if (activity.kind === "WALK" || activity.kind === "TRAVEL") pose.rz=Math.sin(t*7)*0.035
  if (mood === "ANGRY") { pose.x=Math.sin(t*35)*0.009; pose.rz+=Math.sin(t*27)*0.014 }
  if (mood === "HAPPY") pose.y=Math.abs(Math.sin(t*4.2))*0.075
  if (activity.kind === "WALK" || activity.kind === "TRAVEL") { pose.x=0; pose.y=0 }
  if (activity.kind === "RUN") { pose.x=0;pose.y=Math.min(.035,pose.y) }
  return pose
}
// Calibrated screen-space approach: each participant moves twenty pixels toward their partner.
export function closenessPixels(activity, actorId, seconds, motion=true) {
  const pair=interactionActors(activity)
  if(!["CLOSENESS","AFFECTION"].includes(activity?.interaction) || !pair.includes(String(actorId))) return 0
  const t=Math.max(0,Number.isFinite(seconds)?seconds:0)
  const progress=motion ? Math.min(1,t/2.8) : 1
  const hesitation=motion && progress<1 ? Math.sin(t*8)*1.4*Math.sin(progress*Math.PI) : 0
  return Math.min(20,Math.max(0,20*progress+hesitation))
}

// Identical cover scaling for still images and scrolling SVG/image tiles.
export function scrollingBackdrop(width,height,aspect,direction) {
  const ratio=Number.isFinite(aspect) && aspect>0 ? aspect : 1
  const tileWidth=Math.max(width,height*ratio),tileHeight=tileWidth/ratio
  return {width:tileWidth,height:tileHeight,offset:Math.sign(direction)*tileWidth}
}
