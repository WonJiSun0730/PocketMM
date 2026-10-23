import test from "node:test"
import assert from "node:assert/strict"
import { QA_PERSONA_MODEL, qaStartRequest, qaEstimate, qaCostStatus, qaUsd } from "./pocketQa.js"
const cap = { maxTurns: 1000, paidAvailable: true, targetModels: ["gpt-5.6-luna", "gpt-5.6-sol"], perTurnEstimates: { "gpt-5.6-sol": { lowUsd: 0.05, highUsd: 0.06 } } }
test("pending requests are distinct from unresolved charges and tiny embedding costs stay visible", () => {
  assert.equal(qaCostStatus(1, 1), "응답 대기 1건")
  assert.equal(qaCostStatus(1, 0), "과금 미확인 1건")
  assert.equal(qaCostStatus(2, 1), "응답 대기 1건 · 과금 미확인 1건")
  assert.equal(qaUsd(0.0000007), "$0.0000007")
  assert.equal(qaUsd(0.1939), "$0.1939")
  assert.equal(qaUsd(null), "미확인")
})
test("Luna is fixed while target and turn count are configurable", () => {
  assert.equal(QA_PERSONA_MODEL, "gpt-5.6-luna")
  assert.deepEqual(qaStartRequest({ requestId: "id", targetModel: "gpt-5.6-sol", turns: "100", paid: false }, cap), { requestId: "id", targetModel: "gpt-5.6-sol", turns: 100, paid: false, budgetUsd: 0, acknowledgeCost: false })
})
test("paid start requires available pricing, positive budget and consent", () => {
  const request = { targetModel: "gpt-5.6-sol", turns: 20, paid: true, budgetUsd: 2, acknowledgeCost: true }
  assert.equal(qaStartRequest(request, cap).budgetUsd, 2)
  assert.throws(() => qaStartRequest({ ...request, acknowledgeCost: false }, cap))
  assert.throws(() => qaStartRequest({ ...request, budgetUsd: 0 }, cap))
  assert.throws(() => qaStartRequest(request, { ...cap, paidAvailable: false }))
  assert.throws(() => qaStartRequest({ ...request, turns: 1.5 }, cap))
  assert.throws(() => qaStartRequest({ ...request, turns: 1001 }, cap))
})
test("estimate scales with turns and does not invent absent pricing", () => {
  assert.deepEqual(qaEstimate(cap, "gpt-5.6-sol", 100), { low: 5, high: 6 })
  assert.equal(qaEstimate(cap, "unknown", 100), null)
})
