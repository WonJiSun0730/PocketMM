import test from "node:test"
import assert from "node:assert/strict"
import { politeFallback, briefActivity, activityCaption, activityPeriod, legacyBriefLog, needsTokenNotice, hideTokenNoticeToday } from "./pocketAbsenceLog.js"
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

test("screen period and caption are separate without changing polite conversion",()=>{
 const activity={start:"2026-10-14T23:55",end:"2026-10-15T00:10",brief:"밀리는 책을 읽었어요.",summary:"밀리는 책을 읽었다."}
 assert.equal(activityPeriod(activity),"10/14 23:55 ~ 10/15 00:10")
 assert.equal(activityCaption(activity),"밀리는 책을 읽었어요.")
 assert.equal(activity.summary,"밀리는 책을 읽었다.")
})

test("fallback handles past endings and keeps quotes and polite captions intact",()=>{
 assert.equal(politeFallback('책을 읽었다. 쉬었다! 밥을 먹었다 🥗'), '책을 읽었어요. 쉬었어요! 밥을 먹었어요 🥗')
 assert.equal(politeFallback('집에 갔다. 잠을 잤다. 돌아왔다.'),'집에 갔어요. 잠을 잤어요. 돌아왔어요.')
 assert.equal(politeFallback('“쉬었다.”라고 말했다. "밥을 먹었다."'), '“쉬었다.”라고 말했어요. "밥을 먹었다."')
 assert.equal(politeFallback('밀리는 쉬었어요. 오늘은 바다를 보았다.'),'밀리는 쉬었어요. 오늘은 바다를 보았어요.')
 assert.equal(politeFallback('커피를 마셨다. 웃었다. 날씨는 따뜻했다.'),'커피를 마셨어요. 웃었어요. 날씨는 따뜻했어요.')
 assert.equal(politeFallback('바다. 쓰다.'),'바다. 쓰다.')
 assert.equal(activityCaption({brief:'쉬었다.',summary:'쉬었다.'}),'쉬었다.')
})
