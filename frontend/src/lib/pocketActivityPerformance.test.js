import test from "node:test"
import assert from "node:assert/strict"
import {topicEmojis,actorCue,speakingActors,walkDirection,puppetPose} from "./pocketActivityPerformance.js"
const talk={kind:"TALK",start:"2026-10-08T15:00:00",playerInvolved:true,characterIds:[10],actorCues:[
 {actorId:"player",emojis:["🤔","💤"],emotion:"CALM",turn:1},
 {actorId:"10",emojis:["🛏️","✔️"],emotion:"ANGRY",turn:0}]}
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
 assert.deepEqual(puppetPose(null,10,1),{x:0,y:0,rx:0,rz:0})
 assert.deepEqual(puppetPose(talk,10,1,false),{x:0,y:0,rx:0,rz:0})
 const happy={...talk,actorCues:[{actorId:"10",emotion:"HAPPY"}]}
 assert.ok(puppetPose(happy,10,1).y>0 && puppetPose(happy,10,1).y<=.075)
 const sad={...talk,actorCues:[{actorId:"10",emotion:"SAD"}]}
 assert.ok(puppetPose(sad,10,1).rx>0)
})
test("walking stays anchored and replay direction is stable; sleep is a static tucked pose",()=>{
 const walk={...talk,kind:"WALK"}
 assert.equal(puppetPose(walk,10,10).x,0)
 assert.equal(puppetPose(walk,10,10).y,0)
 assert.deepEqual(walkDirection(walk),walkDirection({...walk}))
 assert.ok(puppetPose({...talk,kind:"SLEEP"},10,10).y<0)
})
