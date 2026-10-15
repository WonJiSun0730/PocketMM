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
    messages.unshift(...[...page].reverse())
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
      const reply = { message, variants: (variants[index] ?? []).map(variant => {
        const parse = value => { try { return value ? JSON.parse(value) : null } catch { return null } }
        const before = parse(variant.testMemoryBeforeJson)
        const after = parse(variant.testMemoryAfterJson)
        const flatten = groups => (groups ?? []).flatMap(group => (group.memories ?? []).map(memory => ({
          ...memory, characterId: group.characterId, characterName: group.characterName,
        })))
        const previous = new Map(flatten(before).map(memory => [memory.id, memory]))
        const current = flatten(after)
        const added = current.filter(memory => !previous.has(memory.id))
        const updated = current.filter(memory => previous.has(memory.id) && JSON.stringify(previous.get(memory.id)) !== JSON.stringify(memory))
        return {
          ...variant,
          aiUsageAtGeneration: parse(variant.testDiagnosticsJson),
          memoryInspectorBefore: before,
          memoryInspectorAfter: after,
          memoryDiffAtTurn: before && after ? {
            added, updated,
            note: "저장된 전체 기억 스냅샷 비교입니다. 생성 후 별도 작업으로 발생한 변경은 포함되지 않을 수 있습니다.",
          } : null,
        }
      }) }
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
      "currentSnapshot 필드만 내보내기 시점의 값입니다. 각 variants 항목의 memoryInspectorBefore/After, memoryDiffAtTurn, aiUsageAtGeneration은 생성 시점에 저장된 값입니다.",
      "새 답변은 버전별 AI 사용량 및 기억 스냅샷을 포함합니다. 기존 답변은 소급되지 않습니다.",
      "기억 Diff는 생성 전과 추출 완료 후의 전체 기억 스냅샷 비교입니다.",
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
