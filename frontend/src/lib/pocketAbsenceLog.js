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
export function politeFallback(text = "") {
  const endings = {"하였다":"했어요","했다":"했어요","었다":"었어요","았다":"았어요","였다":"였어요","됐다":"됐어요","갔다":"갔어요","왔다":"왔어요","잤다":"잤어요","봤다":"봤어요","한다":"해요"}
  return String(text).split(/("[^"\n]*"|“[^”\n]*”|‘[^’\n]*’|'[^'\n]*')/g).map((part,index)=>index%2 ? part :
    part.replace(/(하였다|했다|었다|았다|였다|됐다|갔다|왔다|잤다|봤다|한다)(?=\s*(?:[.!?。！？]|$|\p{Extended_Pictographic}))/gu,ending=>endings[ending]).replace(/([가-힣])다(?=\s*(?:[.!?。！？]|$|\p{Extended_Pictographic}))/gu,(whole,syllable)=>(syllable.charCodeAt(0)-0xac00)%28===20 ? `${syllable}어요` : whole)).join("")
}
export function activityCaption(activity) {
  const text = activity.brief || politeFallback(activity.summary ?? "")
  return text
}
export function briefActivity(activity) {
  return `${activityPeriod(activity)} ${activityCaption(activity)}`
}
export function legacyBriefLog(raw = "") {
  // Preserve the legacy content as fallback; hide verbose ISO headers in the visible caption only.
  return politeFallback(raw.replaceAll("[부재 활동]\n","").replace(/(\d{4})-(\d{2})-(\d{2})T(\d{2}:\d{2})(?::\d{2}(?:\.\d+)?)? ~ \d{4}-(\d{2})-(\d{2})T(\d{2}:\d{2})(?::\d{2}(?:\.\d+)?)?[^\n]*\n/g,
    (_,y,m,d,t,em,ed,et)=>`${m}/${d} ${t} ~ ${m===em && d===ed ? "" : `${em}/${ed} `}${et} `)
    )
}
