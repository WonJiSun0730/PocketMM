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

// Only remove large neutral placeholder bands on BOTH edges, never a one-sided sky/wall.
export function backgroundContentBounds(data, width, height) {
  const emptyRow = y => {
    let neutral = 0
    for (let x = 0; x < width; x++) {
      const i = (y * width + x) * 4, r=data[i], g=data[i+1], b=data[i+2]
      if (data[i+3] < 16 || (Math.max(r,g,b)-Math.min(r,g,b) <= 8 && r <= 210)) neutral++
    }
    return neutral >= width * .97
  }
  let top=0, bottom=height
  while(top<height && emptyRow(top)) top++
  while(bottom>top && emptyRow(bottom-1)) bottom--
  if(top < height*.12 || height-bottom < height*.12 || bottom-top < height*.25) return {top:0,bottom:1}
  return {top:top/height,bottom:bottom/height}
}
export function backgroundPeriod(simulatedAt) {
  const match=/T(\d{2}):/.exec(simulatedAt || "")
  if(!match || Number(match[1])>23) return ""
  const hour=Number(match[1])
  return hour<6 || hour>=20 ? "night" : hour<10 ? "morning" : "day"
}
