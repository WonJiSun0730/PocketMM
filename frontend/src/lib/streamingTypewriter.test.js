import test from "node:test"
import assert from "node:assert/strict"
import {createStreamingTypewriter} from "./streamingTypewriter.js"
import {TYPE_INTERVAL_MS as step} from "./typedRoleplay.js"

test("starts on the first received fragment and appends without restarting or jumping at completion",()=>{
  const writer=createStreamingTypewriter()
  assert.equal(writer.update("안녕하세요",0).count,1)
  assert.equal(writer.advance(2*step).count,3)
  const appended=writer.update("안녕하세요 반갑습니다",2*step)
  assert.equal(appended.count,3)
  assert.equal(writer.advance(3*step).count,4)
  const finished=writer.update("안녕하세요 반갑습니다",3*step,true)
  assert.equal(finished.done,false)
  assert.equal(finished.count,4)
  assert.equal(writer.advance(100*step).done,true)
})
test("a network gap earns no credit for text that has not arrived",()=>{
  const writer=createStreamingTypewriter()
  writer.update("a",0);writer.advance(10000)
  assert.equal(writer.update("abcdef",10000).count,2)
  assert.equal(writer.advance(10000+step).count,3)
})
test("skip affects newly received fragments too but never claims server completion",()=>{
  const writer=createStreamingTypewriter()
  writer.update("abcdef",0)
  assert.equal(writer.skip().count,6)
  assert.equal(writer.snapshot().done,false)
  assert.equal(writer.update("abcdefghi",step).count,9)
  assert.equal(writer.update("abcdefghi",step,true).done,true)
})
test("a repaired final reply retains only its shared visible prefix and continues typing",()=>{
  const writer=createStreamingTypewriter()
  writer.update("abcdef",0);writer.advance(20*step)
  const repaired=writer.update("abXYZ",21*step,true)
  assert.equal(repaired.count,3)
  assert.equal(repaired.done,false)
  assert.equal(writer.advance(25*step).done,true)
})
test("emoji are graphemes and a later request starts with a new cursor",()=>{
  const writer=createStreamingTypewriter()
  assert.equal(writer.update("👩‍⚕️안녕",0).total,3)
  assert.equal(writer.snapshot().count,1)
  assert.equal(createStreamingTypewriter().update("새 요청",10000).count,1)
})
