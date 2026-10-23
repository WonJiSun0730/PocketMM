import test from "node:test"
import assert from "node:assert/strict"
import {mergeChatFailures} from "./chatFailures.js"
test("new evidence survives refresh merges while other sessions stay excluded",()=>{
  const row={requestId:"one",sessionId:55,createdAt:"2026-10-23T00:00:00Z",conflicts:["reason"]}
  assert.deepEqual(mergeChatFailures([row],[{requestId:"other",sessionId:56}],55),[row])
  assert.deepEqual(mergeChatFailures([row],[row],55),[row])
  assert.deepEqual(mergeChatFailures([row],[],56),[])
})
test("only the latest ten evidence rows are displayed",()=>{
  const rows=Array.from({length:20},(_,i)=>({requestId:String(i),sessionId:55,createdAt:String(i).padStart(2,"0")}))
  const merged=mergeChatFailures([],rows,55)
  assert.equal(merged.length,10);assert.equal(merged[0].requestId,"19")
})
