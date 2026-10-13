import test from "node:test"
import assert from "node:assert/strict"
import {topicEmojis,actorCue,interactionActors,closenessPixels,speakingActors,speechSide,walkDirection,puppetPose,scrollingBackdrop} from "./pocketActivityPerformance.js"
const talk={kind:"TALK",start:"2026-10-08T15:00:00",playerInvolved:true,characterIds:[10],actorCues:[
 {actorId:"player",emojis:["🤔","💤"],emotion:"CALM",turn:1},
 {actorId:"10",emojis:["🛏️","✔️"],emotion:"ANGRY",turn:0}]}
test("speech rays follow actor facing and keep the chosen frontal side",()=>{
 assert.equal(speechSide(-Math.PI*.12,1),-1)
 assert.equal(speechSide(Math.PI*.12,-1),1)
 assert.equal(speechSide(0,-1),-1)
 assert.equal(speechSide(.01,-1),-1)
 assert.equal(speechSide(0,1),1)
})
test("meal bubbles show one food emoji and fall back to a plate",()=>{
 const meal=emojis=>({kind:"MEAL",actorCues:[{actorId:"player",emojis}]})
 assert.deepEqual(actorCue(meal(["🥗","🍚"]),"player").emojis,["🥗"])
 assert.deepEqual(actorCue(meal(["😊","🍜"]),"player").emojis,["🍜"])
 assert.deepEqual(actorCue(meal(["😊","💤"]),"player").emojis,["🍽️"])
 assert.deepEqual(actorCue(meal([]),"player").emojis,["🍽️"])
})
test("topic icons are actor-specific, at most two graphemes, and reject plain dialogue",()=>{
 assert.deepEqual(actorCue(talk,"player").emojis,["🤔","💤"])
 assert.deepEqual(actorCue(talk,10).emojis,["🛏️","✔️"])
 assert.deepEqual(topicEmojis(["🤔💤🍽️"],"✨"),["🤔","💤"])
 assert.deepEqual(topicEmojis(["some dialogue"],"✨"),["✨"])
 assert.deepEqual(actorCue({kind:"SLEEP"},"player").emojis,["💤"])
})
test("speaker order is deterministic and ignores nonparticipating metadata",()=>{
 assert.deepEqual(speakingActors(talk),["10","player"])
 assert.deepEqual(speakingActors({...talk,playerInvolved:false}),["10"])
})
test("puppet emotions remain bounded and reset when the activity ends",()=>{
 const angry=puppetPose(talk,10,1)
 assert.ok(Math.abs(angry.x)<=.009 && Math.abs(angry.rz)<=.014)
 assert.deepEqual(puppetPose(null,10,1),{x:0,y:0,rx:0,ry:0,rz:0,armRaise:0,stride:0})
 assert.deepEqual(puppetPose(talk,10,1,false),{x:0,y:0,rx:0,ry:0,rz:0,armRaise:0,stride:0})
 const happy={...talk,actorCues:[{actorId:"10",emotion:"HAPPY"}]}
 assert.ok(puppetPose(happy,10,1).y>0 && puppetPose(happy,10,1).y<=.075)
 const sad={...talk,actorCues:[{actorId:"10",emotion:"SAD"}]}
 assert.ok(puppetPose(sad,10,1).rx>0)
})
test("sleep hides under the blanket and activity changes restore visibility",()=>{
 const sleep={...talk,kind:"SLEEP"}
 assert.equal(puppetPose(sleep,10,0).hidden,false)
 assert.equal(puppetPose(sleep,10,1.3).hidden,true)
 assert.equal(puppetPose(sleep,10,0,false).hidden,true)
 assert.ok(!puppetPose(talk,10,0).hidden)
})
test("interaction effects require the selected pair and close by only ten pixels together",()=>{
 const close={...talk,interaction:"CLOSENESS",interactionActorIds:["player","10"]}
 assert.deepEqual(interactionActors(close),["player","10"])
 assert.deepEqual(interactionActors({...close,interactionActorIds:["player","999"]}),[])
 assert.equal(closenessPixels(close,10,0),0)
 assert.equal(closenessPixels(close,10,5)+closenessPixels(close,"player",5),10)
 assert.equal(closenessPixels(close,999,5),0)
 const intimacy={...close,interaction:"INTIMACY"}
 assert.equal(puppetPose(intimacy,10,2).hidden,true)
 assert.equal(puppetPose(intimacy,"player",2).hidden,true)
})
test("meal drink and run move differently and motion reduction is static",()=>{
 for(const kind of ["MEAL","DRINK","COFFEE"]) assert.ok(puppetPose({...talk,kind},10,1).armRaise>0)
 const run=puppetPose({...talk,kind:"RUN"},10,1)
 assert.ok(run.stride!==0 && run.x===0 && run.y>=0 && run.y<=.035)
 assert.equal(puppetPose({...talk,kind:"RUN"},10,1,false).stride,0)
})
test("walking stays anchored and replay direction is stable; sleep is a static tucked pose",()=>{
 const walk={...talk,kind:"WALK"}
 assert.equal(puppetPose(walk,10,10).x,0)
 assert.equal(puppetPose(walk,10,10).y,0)
 assert.deepEqual(walkDirection(walk),walkDirection({...walk}))
 assert.ok(puppetPose({...talk,kind:"SLEEP"},10,10).y<0)
})

test("travel is horizontal, every participant faces the same direction opposite the scenery",()=>{
 const walk={...talk,kind:"WALK"}
 const direction=walkDirection(walk)
 assert.equal(direction[1],0)
 assert.equal(puppetPose(walk,10,1).ry,puppetPose(walk,"player",1).ry)
 assert.ok(puppetPose(walk,10,1).ry*direction[0]<0)
 assert.equal(puppetPose(talk,10,1).ry,0)
})
test("scrolling and still cover scaling fill both axes and loop one whole tile without vertical gaps",()=>{
 const wide=scrollingBackdrop(270,270,3,-1)
 assert.deepEqual(wide,{width:810,height:270,offset:-810})
 const tall=scrollingBackdrop(270,270,.5,1)
 assert.deepEqual(tall,{width:270,height:540,offset:270})
})
