export const issueCategories = { TIME: "시간", CAST: "등장인물", CHARACTER: "캐릭터 설정", PLAYER_CONTROL: "플레이어 조종", PROSE: "지문 품질", OOC: "OOC", OTHER: "기타" }

export function issueEvidence(issue) {
  try { return JSON.parse(issue.evidenceJson) } catch { return {} }
}

function quote(value) {
  const text = typeof value === "string" ? value : JSON.stringify(value ?? null, null, 2)
  const runs = text.match(/`+/g) ?? []
  const fence = "`".repeat(Math.max(3, ...runs.map(run => run.length + 1)))
  return `${fence}\n${text}\n${fence}`
}

export function issuesToMarkdown(issues) {
  return "# PocketMM 대화 오류 사례\n\n" + [...issues].reverse().map(issue => {
    const evidence = issueEvidence(issue)
    const generation = evidence.generation ?? {}
    const context = generation.context ?? {}
    return [
      `## 사례 #${issue.id} · ${issueCategories[issue.category] ?? issue.category}`,
      `기록: ${issue.createdAt} / 포켓: ${issue.sessionId} / 메시지: ${issue.messageId} / 버전: ${issue.variantNo}`,
      `동작: ${generation.operation ?? "이전 기록"} / 모델: ${context.aiProvider ?? "미저장"} · ${generation.effectiveModel ?? context.aiModel ?? "미저장"} / 플레이어 조종 금지: ${context.noControlPlayer == null ? "미저장" : context.noControlPlayer ? "켜짐" : "꺼짐"}`,
      evidence.generationContextCaptured ? "생성 당시 설정을 저장한 기록입니다." : "이전 답변: 생성 당시 설정이 없습니다. 사용자 입력·직전 대화는 기록 시점의 참고값이며 이후 수정됐을 수 있습니다.",
      "### 문제", issue.problem, "### 기대 결과", issue.expected,
      "### 입력 전 장면", quote({ time: evidence.beforeTime, ...evidence.beforePlayerState }),
      "### 사용자 입력", quote(generation.userInput ?? "미저장"),
      "### AI 답변", quote(evidence.reply),
      "### 출력 후 장면", quote({ time: evidence.afterTime, ...evidence.afterPlayerState }),
      "### 직전 대화", quote(context.recentMessages ?? generation.historyAtReportTime ?? []),
      "### 재현 설정·원본 증거", quote(evidence),
    ].join("\n\n")
  }).join("\n\n---\n\n") + "\n"
}

export async function fetchAllIssues() {
  const collected = []
  let before = null
  while (true) {
    const response = await fetch(`/api/worlds/dialogue-issues${before ? `?before=${before}` : ""}`, { credentials: "include" })
    if (!response.ok) throw new Error("문제 기록을 불러오지 못했어요.")
    const page = await response.json()
    collected.push(...page)
    if (page.length < 100) return collected
    before = page[page.length - 1].id
  }
}
