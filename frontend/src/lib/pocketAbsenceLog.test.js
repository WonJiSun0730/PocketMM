import test from "node:test"
import assert from "node:assert/strict"
import { briefActivity, legacyBriefLog, needsTokenNotice, hideTokenNoticeToday } from "./pocketAbsenceLog.js"
test("notice suppression lasts through the local calendar day only",()=>{
  const values=new Map();const storage={getItem:key=>values.get(key),setItem:(key,value)=>values.set(key,value)}
  const today=new Date(2026,9,13,15)
  assert.equal(needsTokenNotice(storage,today),true)
  hideTokenNoticeToday(storage,today)
  assert.equal(needsTokenNotice(storage,new Date(2026,9,13,23,59)),false)
  assert.equal(needsTokenNotice(storage,new Date(2026,9,14,0,0)),true)
})
test("storage failure leaves the token notice enabled",()=>{
  const storage={getItem(){throw Error()},setItem(){throw Error()}}
  assert.equal(needsTokenNotice(storage),true);hideTokenNoticeToday(storage)
})
test("brief captions preserve source and format same-day and cross-day intervals",()=>{
  const activity={start:"2026-10-13T15:56:00",end:"2026-10-13T16:36:00",summary:"산책했다.",brief:"산책하다 뽀뽀했어요! ❤"}
  assert.equal(briefActivity(activity),"10/13 15:56 ~ 16:36 산책하다 뽀뽀했어요! ❤")
  assert.equal(activity.summary,"산책했다.")
  assert.equal(briefActivity({...activity,brief:null,end:"2026-10-14T00:10:00"}),"10/13 15:56 ~ 10/14 00:10 산책했어요.")
})
test("legacy display softens text without modifying its original",()=>{
  const original="[부재 활동]\n🚶 2026-10-13T15:56 ~ 2026-10-13T16:36 · A · Park\nA는 산책했다."
  assert.equal(legacyBriefLog(original),"🚶 10/13 15:56 ~ 16:36 A는 산책했어요.")
  assert.ok(original.includes("A는 산책했다."))
})
