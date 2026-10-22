import test from "node:test"
import assert from "node:assert/strict"
import {readPocketChatStream} from "./pocketChatStream.js"

function response(text, width=1) {
  const bytes=new TextEncoder().encode(text)
  return new Response(new ReadableStream({start(controller) {
    for(let i=0;i<bytes.length;i+=width) controller.enqueue(bytes.slice(i,i+width))
    controller.close()
  }}))
}
test("decodes Korean across byte boundaries and preserves preview/final order",async()=>{
  const events=[]
  await readPocketChatStream(response('{"type":"started"}\n{"type":"preview","reply":"미확정 본문"}\n{"type":"complete","reply":"수정된 본문"}\n'), e=>events.push(e))
  assert.deepEqual(events.map(e=>e.type),["started","preview","complete"])
  assert.equal(events[1].reply,"미확정 본문")
  assert.equal(events[2].reply,"수정된 본문")
})
test("delivers preview before downstream work finishes",async()=>{
  let finish
  let sawPreview=false
  const stream=new ReadableStream({start(controller){
    controller.enqueue(new TextEncoder().encode('{"type":"preview","reply":"본문"}\n'))
    finish=()=>{controller.enqueue(new TextEncoder().encode('{"type":"complete","reply":"본문"}\n'));controller.close()}
  }})
  const reading=readPocketChatStream(new Response(stream),event=>{
    if(event.type==="preview"){sawPreview=true;finish()}
  })
  await reading
  assert.equal(sawPreview,true)
})
test("preview alone is never treated as committed success",async()=>{
  await assert.rejects(readPocketChatStream(response('{"type":"preview","reply":"본문"}\n'),()=>{}),/자동 재시도/)
})
test("failed review and invalid frames fail explicitly without resending",async()=>{
  await assert.rejects(readPocketChatStream(response('{"type":"failed","message":"확인 실패"}\n'),()=>{}),/확인 실패/)
  await assert.rejects(readPocketChatStream(response('not json\n'),()=>{}),SyntaxError)
})
