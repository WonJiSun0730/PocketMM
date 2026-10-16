export function enterSendsMessage(event, environment) {
  return event.key === "Enter" && !event.shiftKey && !event.isComposing && !event.nativeEvent?.isComposing
    && event.keyCode !== 229 && !environment.coarsePointer && !environment.mobile
    && !(environment.touchPoints > 0 && environment.hover === false)
    && !/Android|iPhone|iPad|iPod/i.test(environment.userAgent)
    && !(environment.touchPoints>1 && /Macintosh/i.test(environment.userAgent))
}
export function chatInputEnvironment() {
  return {coarsePointer:window.matchMedia("(pointer: coarse)").matches,
    mobile:navigator.userAgentData?.mobile === true,hover:window.matchMedia("(hover: hover)").matches,
    userAgent:navigator.userAgent,touchPoints:navigator.maxTouchPoints}
}
