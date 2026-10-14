export function noticeDay(now = new Date()) {
  return `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,"0")}-${String(now.getDate()).padStart(2,"0")}`
}
export function needsTokenNotice(storage, now = new Date()) {
  try { return storage.getItem("pocket-absence-token-notice") !== noticeDay(now) } catch { return true }
}
export function hideTokenNoticeToday(storage, now = new Date()) {
  try { storage.setItem("pocket-absence-token-notice",noticeDay(now)) } catch { /* private browsing keeps the notice */ }
}
export function activityPeriod(activity) {
  const start = String(activity.start ?? ""), end = String(activity.end ?? "")
  const from = `${start.slice(5,7)}/${start.slice(8,10)} ${start.slice(11,16)}`
  const to = start.slice(0,10) === end.slice(0,10) ? end.slice(11,16) : `${end.slice(5,7)}/${end.slice(8,10)} ${end.slice(11,16)}`
  return `${from} ~ ${to}`
}
export function activityCaption(activity) {
  const text = activity.brief || String(activity.summary ?? "").replaceAll("했다.","했어요.").replaceAll("한다.","해요.").replaceAll("있었다.","있었어요.")
  return text
}
export function briefActivity(activity) {
  return `${activityPeriod(activity)} ${activityCaption(activity)}`
}
export function legacyBriefLog(raw = "") {
  // Preserve the legacy content as fallback; hide verbose ISO headers in the visible caption only.
  return raw.replaceAll("[부재 활동]\n","").replace(/(\d{4})-(\d{2})-(\d{2})T(\d{2}:\d{2})(?::\d{2}(?:\.\d+)?)? ~ \d{4}-(\d{2})-(\d{2})T(\d{2}:\d{2})(?::\d{2}(?:\.\d+)?)?[^\n]*\n/g,
    (_,y,m,d,t,em,ed,et)=>`${m}/${d} ${t} ~ ${m===em && d===ed ? "" : `${em}/${ed} `}${et} `)
    .replaceAll("했다.","했어요.").replaceAll("한다.","해요.")
}
