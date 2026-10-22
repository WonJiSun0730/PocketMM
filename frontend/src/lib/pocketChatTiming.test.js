import test from "node:test"
import assert from "node:assert/strict"
import {createPocketChatTiming,observeReplyPaint,timingStats,timingSeconds} from "./pocketChatTiming.js"

function fixture(storage = new Map(),sessionId=55) {
  let clock=0,id=0
  const timing=createPocketChatTiming({sessionId,storage:{getItem:k=>storage.get(k),setItem:(k,v)=>storage.set(k,v)},
    now:()=>clock,makeId:()=>`${sessionId}-${++id}`,wallTime:()=>"2026-10-22T09:00:00Z"})
  return {timing,storage,at:value=>{clock=value}}
}
test("records click-to-delivery, paint, completion and input-ready separately",()=>{
  const {timing,at}=fixture()
  at(100);const id=timing.begin()
  at(300);timing.sent(id)
  at(10100);timing.received(id)
  at(10140);timing.displayed(id)
  at(34100);timing.complete(id,"private reply")
  at(35100);timing.ready(id)
  const row=timing.getSnapshot()[0]
  assert.equal(row.sentMs,200)
  assert.equal(row.receivedMs,10000)
  assert.equal(row.displayMs,10040)
  assert.equal(row.completeMs,34000)
  assert.equal(row.readyMs,35000)
  assert.equal(row.status,"COMPLETE")
  assert.equal(JSON.stringify(timing.export()).includes("private reply"),false)
})
test("stats exclude failures and interrupted measurements; zero remains valid",()=>{
  const stats=timingStats([{status:"COMPLETE",receivedMs:0},{status:"COMPLETE",receivedMs:20},
    {status:"FAILED",receivedMs:1000},{status:"INTERRUPTED",receivedMs:500},
    {status:"COMPLETE"},{status:"COMPLETE",receivedMs:-2}],"receivedMs")
  assert.deepEqual(stats,{count:2,min:0,max:20,average:10})
  assert.equal(timingSeconds(0),"0.00초")
  assert.equal(timingSeconds(null),"미측정")
})
test("reload preserves completed records, marks unfinished requests interrupted and separates sessions",()=>{
  const {timing,storage,at}=fixture()
  const first=timing.begin();at(12);timing.received(first);timing.complete(first,"reply");timing.ready(first)
  timing.begin()
  const loaded=fixture(storage).timing.getSnapshot()
  assert.equal(loaded[0].status,"INTERRUPTED")
  assert.equal(loaded[1].status,"COMPLETE")
  assert.equal(fixture(storage,56).timing.getSnapshot().length,0)
})
test("stale callbacks cannot change the next request and missing preview is not invented",()=>{
  const {timing,at}=fixture()
  const old=timing.begin();const current=timing.begin()
  at(10);timing.received(old);timing.displayed(old);timing.complete(old,"old")
  timing.complete(current,"current");timing.ready(current)
  assert.equal(timing.getSnapshot()[0].receivedMs,undefined)
  assert.equal(timing.getSnapshot()[0].displayMs,undefined)
  assert.equal(timing.canMeasureFinal("old"),false)
  assert.equal(timing.canMeasureFinal("current"),true)
})
test("hidden tabs never produce a display estimate; hiding after paint preserves the sample",()=>{
  const {timing}=fixture()
  const a=timing.begin();timing.received(a);timing.displayUnavailable(a);timing.displayed(a)
  assert.equal(timing.getSnapshot()[0].displayMs,undefined)
  const b=timing.begin();timing.received(b);timing.displayed(b);timing.displayUnavailable(b)
  assert.equal(timing.getSnapshot()[0].displayMs,0)
  assert.equal(timing.getSnapshot()[0].displayUnavailable,undefined)
})
test("keeps at most 100 metadata-only records and tolerates disabled storage",()=>{
  const {timing}=fixture()
  for(let i=0;i<103;i++){const id=timing.begin();timing.complete(id,"reply");timing.ready(id)}
  assert.equal(timing.getSnapshot().length,100)
  timing.clear();assert.equal(timing.getSnapshot().length,0)
  const blocked=createPocketChatTiming({sessionId:1,storage:{getItem(){throw Error()},setItem(){throw Error()}},now:()=>0,makeId:()=>"id"})
  const id=blocked.begin();blocked.received(id);blocked.failed(id)
  assert.equal(blocked.getSnapshot()[0].status,"FAILED")
  assert.equal(blocked.storageAvailable(),false)
})
test("display estimate waits two frames with a connected visible DOM and cleans up",()=>{
  let frames=[],removed=0,painted=0
  const doc={visibilityState:"visible",addEventListener(){},removeEventListener(){removed++}}
  const cleanup=observeReplyPaint({isConnected:true},()=>painted++,()=>assert.fail("visible"),
    {document:doc,requestFrame:fn=>{frames.push(fn);return frames.length},cancelFrame(){}})
  frames.shift()();assert.equal(painted,0)
  frames.shift()();assert.equal(painted,1)
  cleanup();assert.equal(removed,1)
})
test("hidden or removed previews are never reported as displayed",()=>{
  let frame,painted=0,unavailable=0
  const doc={visibilityState:"hidden",addEventListener(){},removeEventListener(){}}
  observeReplyPaint({isConnected:true},()=>painted++,()=>unavailable++,{document:doc,requestFrame(){assert.fail("hidden")},cancelFrame(){}})
  assert.equal(unavailable,1)
  doc.visibilityState="visible"
  observeReplyPaint({isConnected:false},()=>painted++,()=>{}, {document:doc,requestFrame:fn=>{frame=fn},cancelFrame(){}})
  frame();assert.equal(painted,0)
})
