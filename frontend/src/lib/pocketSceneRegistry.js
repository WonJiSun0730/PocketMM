// Built-in scene presets. Match specific locations before broad keywords.
// Unknown locations intentionally keep the neutral fallback until generated
// backgrounds are supported; never guess an unrelated authored scene.
export const POCKET_SCENE_PRESETS = Object.freeze({
  counseling: Object.freeze({ id: "counseling", label: "상담실", backdrop: 0x171821, wall: 0xd8c9ae, floor: 0x76533c }),
  lounge: Object.freeze({ id: "lounge", label: "휴게실", backdrop: 0x20202a, wall: 0xc9b8a5, floor: 0x72533e }),
  office: Object.freeze({ id: "office", label: "사무실", backdrop: 0x1c2531, wall: 0xb9c5ca, floor: 0x555e65 }),
  bedroom: Object.freeze({ id: "bedroom", label: "침실", backdrop: 0x252032, wall: 0xc6b5ca, floor: 0x6e5560 }),
  default: Object.freeze({ id: "default", label: "기본 실내", backdrop: 0x171821, wall: 0xd8c9ae, floor: 0x76533c }),
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
