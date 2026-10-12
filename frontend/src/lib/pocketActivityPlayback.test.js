import test from "node:test"
import assert from "node:assert/strict"
import { activitySymbol, activityParticipants, activityModels, nextActivityIndex, replayIncludesPlayer } from "./pocketActivityPlayback.js"

test("all server activity kinds yield fixed emoji bubbles, never summary or dialogue", () => {
  for (const kind of ["WALK", "TALK", "REST", "READ", "WORK", "MEAL", "COFFEE", "WAIT", "SLEEP", "COOK", "CARE", "TRAVEL"]) {
    const symbol = activitySymbol(kind)
    assert.ok(symbol.emoji && symbol.label)
    assert.ok(!/[A-Za-z가-힣]/.test(symbol.emoji))
  }
  assert.equal(activitySymbol("COFFEE").emoji, "☕")
  assert.equal(activitySymbol("REST").emoji, "🛋️")
  assert.equal(activitySymbol("WAIT").emoji, "⏳")
  assert.equal(activitySymbol("SLEEP").emoji, "💤")
  assert.equal(activitySymbol("untrusted dialogue goes here").emoji, "✨")
})
test("absence replay includes only registered participants, including departed NPCs, excluding the player", () => {
  const registered = [{ id: 10, name: "A", modelUrl: "/a.vrm", modelHeight: 1.7 },
    { characterId: 11, characterName: "B", modelUrl: "/b.vrm", modelHeight: 1.6 },
    { id: 12, name: "C", modelUrl: "/c.vrm", modelHeight: 1.8 }]
  const selected = activityParticipants({ characterIds: [10, 11, 10, 999] }, registered)
  assert.deepEqual(selected, registered.slice(0, 2))
  assert.deepEqual(activityModels(selected).map(c => c.url), ["/a.vrm", "/b.vrm"])
  assert.deepEqual(activityParticipants(null, registered), [])
})
test("missing models are detectable without inventing substitute actors", () => {
  const participants = [{ id: 10, modelUrl: "", modelHeight: 1.7 }, { id: 11, modelUrl: "/b.vrm", modelHeight: 0 }]
  assert.deepEqual(activityModels(participants), [])
})
test("ordered playback finishes instead of looping or skipping the final frame", () => {
  assert.equal(nextActivityIndex(0, 2), 1)
  assert.equal(nextActivityIndex(1, 2), null)
  assert.equal(nextActivityIndex(0, 1), null)
  assert.equal(nextActivityIndex(null, 2), null)
  assert.equal(nextActivityIndex(-1, 2), null)
})

test("same-place replay preserves a passive player without placing them in off-screen activity", () => {
  assert.equal(replayIncludesPlayer("심리상담센터", "심리상담센터 상담실"), true)
  assert.equal(replayIncludesPlayer("집", "집"), true)
  assert.equal(replayIncludesPlayer("집", "집 거실"), true)
  assert.equal(replayIncludesPlayer("집", "집무실"), false)
  assert.equal(replayIncludesPlayer("집", "공원"), false)
  assert.equal(replayIncludesPlayer("미설정", "미설정"), false)
  assert.equal(replayIncludesPlayer(null, "상담실"), false)
})
