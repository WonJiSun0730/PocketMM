import test from "node:test"
import assert from "node:assert/strict"
import { newWorldSystem, prepareWorldSystems } from "./worldSystemForm.js"

test("new system includes the required initial value in the actual JSON payload", () => {
  const system = { ...newWorldSystem(), name: "피에타" }
  assert.equal(JSON.parse(JSON.stringify({ systems: prepareWorldSystems([system]) })).systems[0].initialValue, 0)
})
test("preserves selected initial value and rejects blank or invalid numeric fields", () => {
  const system = { ...newWorldSystem(), name: "피에타", initialValue: "37" }
  assert.equal(prepareWorldSystems([system])[0].initialValue, 37)
  for (const value of [null, undefined, "", "1.5", NaN, 101, "2147483648"]) {
    assert.throws(() => prepareWorldSystems([{ ...system, initialValue: value }]))
  }
  assert.throws(() => prepareWorldSystems([{ ...system, minValue: "" }]))
})
test("retains blank-name row behavior but validates named system state bounds", () => {
  assert.deepEqual(prepareWorldSystems([newWorldSystem()]), [])
  assert.throws(() => prepareWorldSystems([{ ...newWorldSystem(), name: "피에타", states: [{ minValue: -1, maxValue: 20, label: "낮음" }] }]))
})
