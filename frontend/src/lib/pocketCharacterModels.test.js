import test from 'node:test'
import assert from 'node:assert/strict'
import {resolvePocketSceneModels} from './pocketCharacterModels.js'
const player={modelUrl:'/models/player.vrm',modelHeight:1.6},character={modelUrl:'/models/a.vrm',modelHeight:1.8}
test('time-check readiness requires player and all Pocket roster models, including absent actors',()=>{
 const ready=resolvePocketSceneModels({playerProfile:player,worldCharacters:[character],cast:[]})
 assert.equal(ready.ready,true)
 for(const profile of [null,{}, {...player,modelUrl:' '},{...player,modelHeight:-1}])
  assert.equal(resolvePocketSceneModels({playerProfile:profile,worldCharacters:[character]}).ready,false)
 assert.equal(resolvePocketSceneModels({playerProfile:player,worldCharacters:[]}).ready,false)
 assert.equal(resolvePocketSceneModels({playerProfile:player,worldCharacters:[character,{}],cast:[character]}).ready,false)
 assert.equal(resolvePocketSceneModels({playerProfile:player,worldCharacters:[character],cast:[{}]}).ready,false)
})
