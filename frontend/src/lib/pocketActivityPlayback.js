export const ACTIVITY_FRAME_MS = 4000

const activities = Object.freeze({
  WALK: { emoji: "🚶", label: "산책" },
  TALK: { emoji: "🗣️", label: "대화" },
  REST: { emoji: "💤", label: "휴식" },
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
