const segmenter = new Intl.Segmenter("ko", { granularity: "grapheme" })
export const TYPE_INTERVAL_MS = 22
export function roleplaySegments(text) {
  return (text ?? "").split(/(\*\*\*[^*]+\*\*\*|\*\*[^*]+\*\*|\*[^*]+\*)/g).map(part => {
    const markers=part.match(/^(\*{1,3})[^*]+\1$/)?.[1]?.length ?? 0
    const action=markers===1 || markers===3
    const bold=markers>=2
    const value=markers ? part.slice(markers,-markers) : part
    return {action,bold,characters:[...segmenter.segment(value)].map(item=>item.segment)}
  })
}
export function visibleCharacterCount(elapsed,total) {
  return Math.max(0,Math.min(total,Math.floor(Math.max(0,elapsed)/TYPE_INTERVAL_MS)))
}
