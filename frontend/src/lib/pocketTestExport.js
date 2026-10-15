async function readJson(url) {
  const response = await fetch(url, { credentials: "include" })
  if (!response.ok) throw new Error(`테스트 데이터 조회 실패: ${response.status} ${url}`)
  return response.json()
}

export async function collectPocketTestData({ base, sessionId, metadata }) {
  const messages = []
  let before = null
  for (;;) {
    const page = await readJson(`${base}/messages${before == null ? "" : `?before=${before}`}`)
    if (!page.length) break
    messages.unshift(...page)
    if (page.length < 15) break
    const next = page[0].id
    if (next === before) throw new Error("대화 페이지가 반복되어 내보내기를 중단했어요.")
    before = next
  }
  const variants = await Promise.all(messages.map(async message => {
    if (message.role !== "CHARACTER") return null
    return readJson(`${base}/messages/${message.id}/variants`)
  }))
  const optional = async url => {
    try { return await readJson(url) }
    catch { return null }
  }
  const [usage, calls, diagnostics, diff, inspector, playerState] = await Promise.all([
    optional(`/api/worlds/pockets/${sessionId}/ai-usage`),
    optional(`/api/worlds/pockets/${sessionId}/ai-usage/calls`),
    optional(`/api/worlds/pockets/${sessionId}/ai-usage/prompt-diagnostics`),
    optional(`${base}/memory-inspector/diff`),
    optional(`${base}/memory-inspector`),
    optional(`/api/worlds/pockets/${sessionId}/player-state`),
  ])
  const turns = []
  for (let index = 0; index < messages.length; index++) {
    const message = messages[index]
    if (message.role === "USER") {
      turns.push({ turn: turns.length + 1, userMessage: message, replies: [] })
    } else if (message.role === "CHARACTER") {
      const reply = { message, variants: variants[index] ?? [] }
      if (turns.length) turns[turns.length - 1].replies.push(reply)
      else turns.push({ turn: 0, userMessage: null, replies: [reply] })
    }
  }
  return {
    schemaVersion: 1,
    exportedAt: new Date().toISOString(),
    pocket: { sessionId, ...metadata },
    turns,
    currentSnapshot: { playerState, usage, calls, promptDiagnostics: diagnostics, memoryDiff: diff, memoryInspector: inspector },
    notes: [
      "모든 대화와 리롤 버전은 서버에 저장된 기록을 조회했습니다.",
      "현재 장면·메모리 Diff·Memory Inspector·AI 사용량은 내보내기 시점의 스냅샷입니다. 과거 각 턴 당시의 상태로 해석하면 안 됩니다.",
      "과거 턴별 AI 사용량·메모리 변경 이력의 정확한 매칭은 별도의 서버 측 턴별 스냅샷 기록이 필요합니다.",
    ],
  }
}

export function downloadPocketTestData(data) {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json;charset=utf-8" })
  const url = URL.createObjectURL(blob)
  const link = document.createElement("a")
  link.href = url
  link.download = `PocketMM-session-${data.pocket.sessionId}-${new Date().toISOString().slice(0, 10)}.json`
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
