// Session-scoped image registry for generated scene backdrops.
// Generation is deliberately separate: only register a URL after the backend
// has successfully generated and persisted an image for this Pocket/location.
const backgrounds = new Map()

export function pocketBackgroundKey(sessionId, location) {
  const normalized = typeof location === "string" ? location.trim().replace(/\s+/g, " ").toLocaleLowerCase() : ""
  return sessionId != null && normalized ? `${sessionId}::${normalized}` : null
}

export function getPocketBackground(sessionId, location) {
  const key = pocketBackgroundKey(sessionId, location)
  return key ? backgrounds.get(key) ?? null : null
}

export function registerPocketBackground(sessionId, location, imageUrl) {
  const key = pocketBackgroundKey(sessionId, location)
  if (!key || typeof imageUrl !== "string" || !imageUrl.trim()) return false
  backgrounds.set(key, imageUrl.trim())
  return true
}

export function clearPocketBackgrounds(sessionId) {
  for (const key of backgrounds.keys()) {
    if (key.startsWith(`${sessionId}::`)) backgrounds.delete(key)
  }
}
