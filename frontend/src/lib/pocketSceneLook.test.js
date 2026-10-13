import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizeLook, lookFilter, LOOK_PRESETS } from './pocketSceneLook.js'
test('scene grading defaults are cozy, bounded and can return to neutral', () => {
  assert.deepEqual(normalizeLook(null), normalizeLook(LOOK_PRESETS.cozy))
  assert.equal(lookFilter(LOOK_PRESETS.original), 'brightness(1) sepia(0) saturate(1) contrast(1)')
  assert.deepEqual(normalizeLook({ brightness: 900, warmth: -9, saturation: 'bad', contrast: 0 }),
    { brightness: 160, warmth: 0, saturation: 112, contrast: 80 })
})
