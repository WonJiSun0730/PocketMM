import test from "node:test"
import assert from "node:assert/strict"
import {enterSendsMessage} from "./chatInput.js"
const desktop={coarsePointer:false,userAgent:"Windows",touchPoints:0}
test("mobile Enter stays a newline even with hardware keyboard or iPad desktop agent",()=>{
 for (const env of [{...desktop,mobile:true},{...desktop,touchPoints:1,hover:false},{...desktop,coarsePointer:true},{...desktop,userAgent:"Android"},{...desktop,userAgent:"iPhone"},{...desktop,userAgent:"Macintosh",touchPoints:5}])
  assert.equal(enterSendsMessage({key:"Enter"},env),false)
})
test("PC Enter sends, Shift Enter and IME confirmation never send",()=>{
 assert.equal(enterSendsMessage({key:"Enter"},desktop),true)
 assert.equal(enterSendsMessage({key:"Enter"},{...desktop,touchPoints:10,hover:true}),true)
 for (const event of [{key:"Enter",shiftKey:true},{key:"Enter",nativeEvent:{isComposing:true}},{key:"Enter",keyCode:229}])
  assert.equal(enterSendsMessage(event,desktop),false)
})
