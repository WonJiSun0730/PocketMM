const LIMIT = 100
const validMs = value => Number.isFinite(value) && value >= 0
const keyFor = sessionId => `pocket-chat-timing:v1:${sessionId}`
export const timingFields = ["sentMs", "receivedMs", "displayMs", "completeMs", "readyMs", "endedMs"]

export function timingStats(records, field) {
  const values = records.filter(row => row.status === "COMPLETE" && validMs(row[field])).map(row => row[field])
  return values.length ? { count: values.length, min: Math.min(...values), max: Math.max(...values),
    average: values.reduce((a,b) => a+b,0)/values.length } : null
}
export function timingSeconds(value) {
  return validMs(value) ? `${(value/1000).toFixed(2)}초` : "미측정"
}

// Only timing metadata is stored: no message, reply, credentials or account balance.
export function createPocketChatTiming({ sessionId, storage, now = () => performance.now(),
  wallTime = () => new Date().toISOString(), makeId = () => crypto.randomUUID() }) {
  const listeners = new Set()
  let records = []
  let storageAvailable = true
  let active = null
  try {
    const saved = JSON.parse(storage.getItem(keyFor(sessionId)) ?? "[]")
    if (Array.isArray(saved)) records = saved.filter(row => row && typeof row.id === "string")
      .slice(0,LIMIT).map(row => {
        const safe = { id: row.id, startedAt: row.startedAt, status: ["RUNNING","FINALIZED"].includes(row.status)
          ? "INTERRUPTED" : row.status, displayUnavailable: Boolean(row.displayUnavailable) }
        for (const field of timingFields) if (validMs(row[field])) safe[field] = row[field]
        return safe
      })
  } catch { storageAvailable = false }
  function publish() {
    try { storage.setItem(keyFor(sessionId),JSON.stringify(records)); storageAvailable = true }
    catch { storageAvailable = false }
    listeners.forEach(listener => listener())
  }
  function update(id, patch) {
    if (!active || active.id !== id) return
    records = records.map(row => row.id === id ? {...row,...patch} : row)
    publish()
  }
  function elapsed() { return Math.max(0,now()-active.start) }
  const api = {
    subscribe(listener) { listeners.add(listener); return () => listeners.delete(listener) },
    getSnapshot: () => records,
    storageAvailable: () => storageAvailable,
    begin() {
      const id = makeId()
      active = {id,start:now(),reply:null}
      records = [{id,startedAt:wallTime(),status:"RUNNING"},...records].slice(0,LIMIT)
      publish()
      return id
    },
    sent(id) { if (active?.id === id) update(id,{sentMs:elapsed()}) },
    received(id) {
      if (active?.id === id && records[0]?.receivedMs == null) update(id,{receivedMs:elapsed()})
    },
    displayUnavailable(id) {
      const row=records.find(row=>row.id===id)
      if(row && row.displayMs == null && !row.displayUnavailable) update(id,{displayUnavailable:true})
    },
    displayed(id) {
      const row = records.find(row => row.id === id)
      if (active?.id === id && row && row.displayMs == null && !row.displayUnavailable
          && row.receivedMs != null) update(id,{displayMs:elapsed()})
    },
    complete(id,reply) {
      if (active?.id !== id) return
      active.reply = reply
      update(id,{completeMs:elapsed(),status:"FINALIZED"})
    },
    canMeasureFinal(reply) { return active?.reply != null && active.reply === reply },
    ready(id) { if(active?.id === id) update(id,{readyMs:elapsed(),endedMs:elapsed(),status:"COMPLETE"}) },
    failed(id) { if(active?.id === id) update(id,{endedMs:elapsed(),status:"FAILED"}) },
    clear() { if(!["RUNNING","FINALIZED"].includes(records[0]?.status)) {active=null;records=[];publish()} },
    export() { return {version:1,sessionId,source:"BROWSER_POCKET_CHAT_STREAM",records} },
  }
  return api
}

// Two animation frames approximate a paint opportunity, not physical display latency.
export function observeReplyPaint(node, onPaint, onUnavailable, {
  document: doc = document, requestFrame = requestAnimationFrame, cancelFrame = cancelAnimationFrame,
} = {}) {
  if (doc.visibilityState !== "visible") { onUnavailable(); return () => {} }
  let frame
  let cancelled = false
  function visibilityChanged() {
    if (doc.visibilityState !== "visible") {cancelled=true;cancelFrame(frame);onUnavailable()}
  }
  doc.addEventListener("visibilitychange",visibilityChanged)
  frame = requestFrame(() => {
    if (cancelled || !node.isConnected) return
    frame = requestFrame(() => {
      if (!cancelled && node.isConnected && doc.visibilityState === "visible") onPaint()
    })
  })
  return () => { cancelled=true;cancelFrame(frame);doc.removeEventListener("visibilitychange",visibilityChanged) }
}
