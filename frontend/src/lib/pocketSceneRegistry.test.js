import test from "node:test"
import assert from "node:assert/strict"
import { existsSync } from "node:fs"
import { POCKET_SCENE_PRESETS, resolvePocketScene, resolvePocketDaypart } from "./pocketSceneRegistry.js"

test("location presets resolve to bundled 2D images and unknown places stay neutral", () => {
  assert.equal(resolvePocketScene("상담실").id, "counseling")
  assert.equal(resolvePocketScene("office").id, "office")
  assert.equal(resolvePocketScene("휴게실").id, "lounge")
  assert.equal(resolvePocketScene("침실").id, "bedroom")
  assert.equal(resolvePocketScene("학교로 향하는 길").id, "default")
  assert.equal(resolvePocketScene(null).id, "default")
  for (const preset of Object.values(POCKET_SCENE_PRESETS)) {
    assert.ok(existsSync(new URL(`../../public${preset.backgroundUrl}`, import.meta.url)))
  }
})

test("background and actor lighting use the story hour including overnight hours", () => {
  for (const hour of [0, 5, 21, 23]) assert.equal(resolvePocketDaypart(`2026-11-04T${String(hour).padStart(2, "0")}:00:00`), "night")
  for (const hour of [6, 12, 16]) assert.equal(resolvePocketDaypart(`2026-11-04T${String(hour).padStart(2, "0")}:00:00`), "day")
  for (const hour of [17, 20]) assert.equal(resolvePocketDaypart(`2026-11-04T${hour}:00:00`), "evening")
  assert.equal(resolvePocketDaypart(""), "day")
  assert.equal(resolvePocketDaypart("2026-11-04T99:00:00"), "day")
})
