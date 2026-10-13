// A 3:2 stage spans 1.5 square viewport widths, with matching alpha-overlay geometry.
export function backgroundGeometry(width, height, direction = 1) {
  const stageWidth = Math.max(width * 1.5, height * 1.5)
  const stageHeight = stageWidth / 1.5
  return { width: stageWidth, height: stageHeight, offset: Math.sign(direction) * stageWidth }
}

export function normalizeSceneAsset(asset) {
  if (!asset || !asset.backgroundUrl || !asset.sceneryUrl) return null
  // Server-owned same-origin assets only; no external tracking URL or javascript scheme.
  if (![asset.backgroundUrl, asset.sceneryUrl].every(url => /^\/api\/worlds\/pockets\/\d+\/backgrounds\/images\/[a-f0-9]{32}\/(background|scenery)$/.test(url))) return null
  return asset
}
