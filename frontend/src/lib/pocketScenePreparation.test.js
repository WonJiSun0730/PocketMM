import test from "node:test"
import assert from "node:assert/strict"
import { loadSceneImages, scenePreparationState } from "./pocketScenePreparation.js"
const asset = { backgroundUrl:"/background", sceneryUrl:"/scenery" }

test("no stage is revealed until both raster layers and the models are ready", () => {
  const scene = { loaded:true, status:"READY", asset, automatic:true, available:true }
  assert.equal(scenePreparationState(scene,{ready:false}),"LOADING")
  assert.equal(scenePreparationState(scene,{ready:true},false),"LOADING")
  assert.equal(scenePreparationState(scene,{ready:true},true),"READY")
  assert.equal(scenePreparationState({...scene,asset:null,status:"GENERATING"},{ready:false}),"LOADING")
  assert.equal(scenePreparationState({...scene,asset:null,status:"FAILED"},{ready:false}),"ERROR")
  assert.equal(scenePreparationState(scene,{ready:false,error:"image failed"}),"ERROR")
  assert.equal(scenePreparationState({...scene,asset:null,status:"MISSING",available:false},{ready:false}),"ERROR")
})
test("both images must finish loading, not just the generation response", async () => {
  const images = []
  let ready = false
  const loading = loadSceneImages(asset,{createImage:()=>{const image={naturalWidth:240};images.push(image);return image}}).then(()=>{ready=true})
  images[0].onload(); await Promise.resolve()
  assert.equal(ready,false)
  images[1].onload(); await loading
  assert.equal(ready,true)
})
test("a broken layer prevents reveal and an abandoned scene cancels preparation", async () => {
  const images = [], controller = new AbortController()
  const loading = loadSceneImages(asset,{signal:controller.signal,createImage:()=>{const image={naturalWidth:240};images.push(image);return image}})
  images[0].onload();images[1].onerror()
  await assert.rejects(loading,/불러오지/)
  const cancelled = loadSceneImages(asset,{signal:controller.signal,createImage:()=>({naturalWidth:240})})
  controller.abort()
  await assert.rejects(cancelled,{name:"AbortError"})
})
