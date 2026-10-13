import test from "node:test"
import assert from "node:assert/strict"
import { backgroundGeometry, normalizeSceneAsset } from "./pocketBackgroundLayers.js"
import { walkDirection, puppetPose } from "./pocketActivityPerformance.js"

test("square stage is 1.5 screens wide, keeps proportions and scrolls horizontally", () => {
  const right = backgroundGeometry(300, 300, 1)
  assert.deepEqual(right, { width:450, height:300, offset:450 })
  assert.equal(backgroundGeometry(300,300,-1).offset,-450)
  const mobile = backgroundGeometry(215,215)
  assert.equal(mobile.width,322.5)
  assert.equal(mobile.height,215)
  const wide = backgroundGeometry(700,260)
  assert.ok(wide.width>=1050 && wide.height>=260)
  assert.equal(wide.width/wide.height,1.5)
  const activity = { kind:"WALK", start:"2026-10-13T14:00", playerInvolved:true, characterIds:[10] }
  const direction = walkDirection(activity)[0]
  assert.ok(backgroundGeometry(300,300,direction).offset * puppetPose(activity,"player",1).ry < 0)
})
test("only authenticated server layer URLs are accepted", () => {
  const prefix = `/api/worlds/pockets/9/backgrounds/images/${"a".repeat(32)}/`
  const asset = { backgroundUrl:prefix+"background", sceneryUrl:prefix+"scenery" }
  assert.equal(normalizeSceneAsset(asset), asset)
  assert.equal(normalizeSceneAsset({...asset,sceneryUrl:"https://outside.example/tracker"}),null)
  assert.equal(normalizeSceneAsset({...asset,backgroundUrl:"javascript:alert(1)"}),null)
  assert.equal(normalizeSceneAsset(null),null)
})
