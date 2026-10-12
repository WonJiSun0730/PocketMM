// Built-in scene presets. Match specific locations before broad keywords.
// Unknown locations intentionally keep the neutral fallback until generated
// backgrounds are supported; never guess an unrelated authored scene.
export const POCKET_SCENE_PRESETS = Object.freeze({
  counseling: Object.freeze({ id: "counseling", label: "상담실", backgroundUrl: "/backgrounds/counseling.svg" }),
  lounge: Object.freeze({ id: "lounge", label: "휴게실", backgroundUrl: "/backgrounds/lounge.svg" }),
  office: Object.freeze({ id: "office", label: "사무실", backgroundUrl: "/backgrounds/office.svg" }),
  bedroom: Object.freeze({ id: "bedroom", label: "침실", backgroundUrl: "/backgrounds/bedroom.svg" }),
  default: Object.freeze({ id: "default", label: "기본 실내", backgroundUrl: "/backgrounds/default.svg" }),
})

const LOCATION_RULES = [
  { pattern: /상담실|counsel(?:ing|ling)\s*room/i, preset: "counseling" },
  { pattern: /휴게실|라운지|lounge|break\s*room/i, preset: "lounge" },
  { pattern: /사무실|집무실|office/i, preset: "office" },
  { pattern: /침실|bedroom/i, preset: "bedroom" },
]

export function resolvePocketScene(location) {
  const value = typeof location === "string" ? location.trim() : ""
  if (!value) return POCKET_SCENE_PRESETS.default
  const match = LOCATION_RULES.find(({ pattern }) => pattern.test(value))
  return POCKET_SCENE_PRESETS[match?.preset ?? "default"]
}

// Use the Pocket's displayed local story hour, not the device timezone.
export function resolvePocketDaypart(simulatedAt) {
  const match = typeof simulatedAt === "string" ? simulatedAt.match(/T(\d{2}):/) : null
  const hour = match ? Number(match[1]) : 12
  if (hour < 0 || hour > 23) return "day"
  if (hour < 6 || hour >= 21) return "night"
  return hour < 17 ? "day" : "evening"
}
