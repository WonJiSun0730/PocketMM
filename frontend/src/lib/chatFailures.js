export function mergeChatFailures(current,incoming,sessionId) {
  const byId=new Map()
  for(const row of [...incoming,...current]) {
    if(!row || String(row.sessionId)!==String(sessionId) || typeof row.requestId!=="string")continue
    if(!byId.has(row.requestId))byId.set(row.requestId,row)
  }
  return [...byId.values()].sort((a,b)=>String(b.createdAt).localeCompare(String(a.createdAt))).slice(0,10)
}
