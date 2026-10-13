import test from "node:test"
import assert from "node:assert/strict"
import { backgroundGeometry, normalizeSceneAsset, backgroundContentBounds, backgroundPeriod } from "./pocketBackgroundLayers.js"
import { walkDirection, puppetPose } from "./pocketActivityPerformance.js"
test("large neutral placeholder bands are cropped together, but one-sided sky is preserved", () => {
  const width=24,height=16,data=new Uint8ClampedArray(width*height*4)
  for(let y=0;y<height;y++)for(let x=0;x<width;x++) {
    const i=(y*width+x)*4, band=y<4 || y>=12
    data[i]=band?80:180;data[i+1]=band?80:120;data[i+2]=band?80:60;data[i+3]=255
  }
  assert.deepEqual(backgroundContentBounds(data,width,height),{top:.25,bottom:.75})
  for(let y=12;y<16;y++)for(let x=0;x<width;x++)data[(y*width+x)*4+1]=120
  assert.deepEqual(backgroundContentBounds(data,width,height),{top:0,bottom:1})
})
test("background periods use scene time rather than place name or wall clock", () => {
  for(const [hour,period] of [[0,'night'],[5,'night'],[6,'morning'],[9,'morning'],[10,'day'],[19,'day'],[20,'night']])
    assert.equal(backgroundPeriod(`2026-10-13T${String(hour).padStart(2,'0')}:00`),period)
  assert.equal(backgroundPeriod(''),'')
})

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
