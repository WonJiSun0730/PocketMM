import test from "node:test"
import assert from "node:assert/strict"
import {roleplaySegments,visibleCharacterCount,TYPE_INTERVAL_MS} from "./typedRoleplay.js"
test("typing preserves RP formatting and Unicode glyphs without exposing control asterisks",()=>{
 const parts=roleplaySegments("*밀리가 👩‍🍳를 보았다.* 밀리: 안녕!")
 assert.equal(parts[1].action,true)
 assert.ok(parts[1].characters.includes("👩‍🍳"))
 assert.ok(!parts.flatMap(p=>p.characters).includes("*"))
})
test("typing advances by elapsed time, never past completion, and supports empty replies",()=>{
 assert.equal(visibleCharacterCount(TYPE_INTERVAL_MS,8),1)
 assert.equal(visibleCharacterCount(99999,8),8)
 assert.equal(visibleCharacterCount(-1,8),0)
 assert.equal(visibleCharacterCount(999,0),0)
})
