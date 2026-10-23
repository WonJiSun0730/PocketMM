import test from "node:test"
import assert from "node:assert/strict"
import {createReplySuggestions,wrapAction} from "./replySuggestions.js"
const tick=()=>new Promise(resolve=>setTimeout(resolve,0))
const ready={status:"READY",suggestions:["가","나","다"]}
test("default off costs zero; three choices share one request across toggles",async()=>{
  let calls=0
  const manager=createReplySuggestions(async()=>{calls++;return ready})
  manager.setScene({key:"1",source:{messageId:1}})
  await tick();assert.equal(calls,0);assert.equal(manager.getSnapshot().enabled,false)
  manager.toggle();await tick();assert.equal(calls,1)
  assert.deepEqual(manager.getSnapshot().suggestions,ready.suggestions)
  manager.toggle();manager.toggle();await tick();assert.equal(calls,1)
})
test("in-flight toggle shares request; late previous-scene results never replace current choices",async()=>{
  const finish=new Map(),calls=[]
  const manager=createReplySuggestions(source=>{calls.push(source);return new Promise(resolve=>finish.set(source,resolve))})
  manager.setScene({key:"old",source:"old"});manager.toggle();await tick()
  manager.toggle();manager.toggle();await tick();assert.deepEqual(calls,["old"])
  manager.setScene({key:"new",source:"new"});await tick()
  finish.get("new")(ready);await tick()
  finish.get("old")({...ready,suggestions:["이전1","이전2","이전3"]});await tick()
  assert.equal(manager.getSnapshot().key,"new");assert.deepEqual(manager.getSnapshot().suggestions,ready.suggestions)
})
test("enabled new passage loads once; disabled new passages and suspended input do not load",async()=>{
  let calls=0
  const manager=createReplySuggestions(async()=>{calls++;return ready})
  manager.setScene({key:"1"});manager.toggle();await tick()
  manager.setScene(null);manager.setScene({key:"1"});await tick();assert.equal(calls,1)
  manager.setScene({key:"2"});await tick();assert.equal(calls,2)
  manager.toggle();manager.setScene({key:"3"});await tick();assert.equal(calls,2)
  manager.toggle();await tick();assert.equal(calls,3)
})
test("failed generation is not retried by toggling",async()=>{
  let calls=0
  const manager=createReplySuggestions(async()=>{calls++;throw new Error("failed")})
  manager.setScene({key:"1"});manager.toggle();await tick()
  manager.toggle();manager.toggle();await tick()
  assert.equal(calls,1);assert.equal(manager.getSnapshot().status,"FAILED")
})
test("turning off before dispatch avoids a paid request",async()=>{
  let calls=0
  const manager=createReplySuggestions(async()=>{calls++;return ready})
  manager.setScene({key:"1"});manager.toggle();manager.toggle();await tick()
  assert.equal(calls,0)
  manager.toggle();await tick();assert.equal(calls,1)
})
test("asterisk wraps selection and keeps cursor/selection inside, including emoji",()=>{
  assert.deepEqual(wrapAction("가나다",1,2),{value:"가*나*다",start:2,end:3})
  assert.deepEqual(wrapAction("가나다",1),{value:"가**나다",start:2,end:2})
  assert.deepEqual(wrapAction("",0),{value:"**",start:1,end:1})
  assert.deepEqual(wrapAction("A😀B",1,3),{value:"A*😀*B",start:2,end:4})
})
