export const ACTIVITY_FRAME_MS = 4000

const activities = Object.freeze({
  WALK: { emoji: "🚶", label: "산책" },
  TALK: { emoji: "🗣️", label: "대화" },
  REST: { emoji: "🛋️", label: "휴식" },
  WAIT: { emoji: "⏳", label: "기다림" },
  SLEEP: { emoji: "💤", label: "수면" },
  COOK: { emoji: "🍳", label: "요리" },
  CARE: { emoji: "🩺", label: "돌봄·치료" },
  TRAVEL: { emoji: "🚗", label: "이동" },
  READ: { emoji: "📖", label: "독서" },
  WORK: { emoji: "📝", label: "일" },
  MEAL: { emoji: "🍽️", label: "식사" },
  COFFEE: { emoji: "☕", label: "커피" },
})
export function activitySymbol(kind) {
  return activities[kind] ?? { emoji: "✨", label: "활동" }
}
export function activityParticipants(activity, registeredCharacters = []) {
  const ids = new Set(activity?.characterIds ?? [])
  return registeredCharacters.filter(character => ids.has(character.id ?? character.characterId))
}
export function activityModels(participants) {
  return participants.filter(c => c.modelUrl && Number(c.modelHeight) > 0).map(source => ({
    url: source.modelUrl, height: Number(source.modelHeight), source,
  }))
}
export function nextActivityIndex(index, count) {
  return Number.isInteger(index) && index >= 0 && index + 1 < count ? index + 1 : null
}

// Keep a passive player in the established place, including a named room within it.
// A shared preset (e.g. two offices) alone is not evidence of the same place.
export function replayIncludesPlayer(playerLocation, activityLocation) {
  const normalize = value => typeof value === "string" ? value.trim().replace(/\s+/g, " ").toLowerCase() : ""
  const player = normalize(playerLocation), activity = normalize(activityLocation)
  if (!player || !activity || ["미설정", "없음", "unknown"].includes(player) || ["미설정", "없음", "unknown"].includes(activity)) return false
  return player === activity || activity.startsWith(`${player} `) || player.startsWith(`${activity} `)
}
