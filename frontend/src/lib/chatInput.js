export function enterSendsMessage(event, environment) {
  return event.key === "Enter" && !event.shiftKey && !event.isComposing && !event.nativeEvent?.isComposing
    && event.keyCode !== 229 && !environment.coarsePointer
    && !/Android|iPhone|iPad|iPod/i.test(environment.userAgent)
    && !(environment.touchPoints>1 && /Macintosh/i.test(environment.userAgent))
}
export function chatInputEnvironment() {
  return {coarsePointer:window.matchMedia("(pointer: coarse)").matches,
    userAgent:navigator.userAgent,touchPoints:navigator.maxTouchPoints}
}
