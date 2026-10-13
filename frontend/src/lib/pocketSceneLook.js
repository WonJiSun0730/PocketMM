export const LOOK_KEY = "pocketmm.scene-look.v1"
export const LOOK_PRESETS = {
  original: { label: "원본", brightness: 100, warmth: 0, saturation: 100, contrast: 100 },
  cozy: { label: "포근하게", brightness: 118, warmth: 18, saturation: 112, contrast: 94 },
  clear: { label: "선명하게", brightness: 112, warmth: 3, saturation: 125, contrast: 108 },
  soft: { label: "부드럽게", brightness: 120, warmth: 10, saturation: 95, contrast: 88 },
}
export function normalizeLook(value) {
  const source = value && typeof value === "object" ? value : LOOK_PRESETS.cozy
  const limits = { brightness: [80, 160], warmth: [0, 40], saturation: [60, 150], contrast: [80, 120] }
  return Object.fromEntries(Object.entries(limits).map(([key, [min, max]]) => {
    const n = Number(source[key]); return [key, Number.isFinite(n) ? Math.max(min, Math.min(max, n)) : LOOK_PRESETS.cozy[key]]
  }))
}
export function lookFilter(value) {
  const v = normalizeLook(value)
  return `brightness(${v.brightness / 100}) sepia(${v.warmth / 100}) saturate(${v.saturation / 100}) contrast(${v.contrast / 100})`
}
