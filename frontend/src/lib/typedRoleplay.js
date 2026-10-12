const segmenter = new Intl.Segmenter("ko", { granularity: "grapheme" })
export const TYPE_INTERVAL_MS = 22
export function roleplaySegments(text) {
  return (text ?? "").split(/(\*[^*]+\*)/g).map(part => {
    const action=part.startsWith("*") && part.endsWith("*") && part.length>2
    const value=action ? part.slice(1,-1) : part
    return {action,characters:[...segmenter.segment(value)].map(item=>item.segment)}
  })
}
export function visibleCharacterCount(elapsed,total) {
  return Math.max(0,Math.min(total,Math.floor(Math.max(0,elapsed)/TYPE_INTERVAL_MS)))
}
