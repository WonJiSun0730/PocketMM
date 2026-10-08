import test from "node:test"
import assert from "node:assert/strict"
import { issuesToMarkdown, fetchAllIssues } from "./dialogueIssueExport.js"

test("exports expected result, exact evidence, and handles embedded Markdown fences", () => {
  const issue = { id: 1, sessionId: 2, messageId: 3, variantNo: 2, category: "TIME", createdAt: "2026-10-08T15:00", problem: "날짜 누락", expected: "10월 11일 21:00", evidenceJson: JSON.stringify({ generationContextCaptured: true, generation: { userInput: "*3일 후*\n```\ninput", effectiveModel: "actual-model", context: { aiProvider: "GEMINI", noControlPlayer: false } }, reply: "Reply", beforeTime: "2026-10-08T15:30", afterTime: "2026-10-08T15:30" }) }
  const output = issuesToMarkdown([issue])
  assert.ok(output.includes("10월 11일 21:00")); assert.ok(output.includes("actual-model"))
  assert.ok(output.includes("버전: 2")); assert.ok(output.includes("````\n*3일 후*\n```\ninput\n````"))
})

test("export follows pagination so records after the first hundred are retained", async () => {
  const original = globalThis.fetch
  const urls = []
  globalThis.fetch = async url => { urls.push(url); return { ok: true, json: async () => urls.length === 1 ? Array.from({ length: 100 }, (_, i) => ({ id: 200 - i })) : [{ id: 100 }] } }
  try {
    const result = await fetchAllIssues()
    assert.equal(result.length, 101); assert.equal(result.at(-1).id, 100)
    assert.equal(urls[1], "/api/worlds/dialogue-issues?before=101")
  } finally { globalThis.fetch = original }
})
