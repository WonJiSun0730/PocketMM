import test from "node:test"
import assert from "node:assert/strict"
import { QA_PERSONA_MODEL, qaStartRequest, qaEstimate } from "./pocketQa.js"
const cap = { maxTurns: 1000, paidAvailable: true, targetModels: ["gpt-5.6-luna", "gpt-5.6-sol"], perTurnEstimates: { "gpt-5.6-sol": { lowUsd: 0.05, highUsd: 0.06 } } }
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
