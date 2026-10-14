import test from 'node:test'
import assert from 'node:assert/strict'
import { sceneTransition, hasSceneTransition } from './pocketActivityPerformance.js'
test('outings use nearest edge and arrivals return from the same edge',()=>{
 assert.ok(sceneTransition('DEPART',1,-1,4).x<0)
 assert.ok(sceneTransition('DEPART',1,1,4).x>0)
 assert.equal(sceneTransition('DEPART',4,1,4).hidden,true)
 assert.equal(sceneTransition('ARRIVE',0,1,4).x,4)
 assert.equal(sceneTransition('ARRIVE',4,1,4).x,0)
 assert.equal(sceneTransition('ARRIVE',0,1,4,true,-1).side,-1)
 assert.equal(sceneTransition('ARRIVE',0,1,4,false).x,0)
 assert.equal(sceneTransition('DEPART',0,1,4,false).hidden,true)
})
test('only participating transition cues stop background movement',()=>{
 assert.equal(hasSceneTransition({playerInvolved:true,actorCues:[{actorId:'player',pose:'DEPART'}]}),true)
 assert.equal(hasSceneTransition({playerInvolved:true,actorCues:[{actorId:'9',pose:'DEPART'}]}),false)
 assert.equal(hasSceneTransition({playerInvolved:true,actorCues:[{actorId:'player',pose:'WAVE'}]}),false)
})

test('departure turns before stepping, uses visible gait, and arrival settles',()=>{
 const turning=sceneTransition('DEPART',.2,1,4)
 assert.equal(turning.x,0)
 assert.equal(turning.moving,false)
 assert.ok(turning.turn>0 && turning.turn<1)
 const walking=sceneTransition('DEPART',.65,1,4)
 assert.equal(walking.turn,1)
 assert.ok(Math.abs(walking.stride)>.4)
 assert.equal(walking.moving,true)
 assert.equal(sceneTransition('ARRIVE',4,1,4).stride,0)
})
