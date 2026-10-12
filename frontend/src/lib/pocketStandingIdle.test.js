import test from "node:test"
import assert from "node:assert/strict"
import { Bone, Object3D, Euler } from "three"
import { createStandingIdle, settleStandingPhysics } from "./pocketStandingIdle.js"

function rig() {
  const names = ["leftUpperArm", "rightUpperArm", "leftLowerArm", "rightLowerArm", "spine", "chest", "head", "hips", "leftFoot"]
  const bones = new Map(names.map(name => [name, new Bone()]))
  const scene = new Object3D()
  scene.position.set(3, 0.2, 1)
  scene.scale.setScalar(1.4)
  const deltas = []
  return { bones, scene, deltas, humanoid: { getNormalizedBoneNode: name => bones.get(name) }, update: delta => deltas.push(delta) }
}

test("standing lowers arms while keeping root, hips, feet and scale unchanged", () => {
  const vrm = rig()
  const root = vrm.scene.position.clone()
  const update = createStandingIdle(vrm)
  assert.ok(new Euler().setFromQuaternion(vrm.bones.get("leftUpperArm").quaternion).z < -1)
  assert.ok(new Euler().setFromQuaternion(vrm.bones.get("rightUpperArm").quaternion).z > 1)
  update(5, 1 / 30)
  assert.deepEqual(vrm.scene.position, root)
  assert.equal(vrm.scene.scale.x, 1.4)
  assert.equal(vrm.bones.get("hips").quaternion.w, 1)
  assert.equal(vrm.bones.get("leftFoot").quaternion.w, 1)
})

test("idle is animated, deterministic, bounded and does not accumulate bone rotation", () => {
  const vrm = rig()
  const update = createStandingIdle(vrm)
  update(0, 0)
  const first = vrm.bones.get("chest").quaternion.clone()
  update(1, 0.03)
  const second = vrm.bones.get("chest").quaternion.clone()
  assert.notDeepEqual(first, second)
  for (let i = 0; i < 200; i++) update(1, 0.03)
  assert.ok(vrm.bones.get("chest").quaternion.equals(second))
  update(100, 30)
  assert.ok(vrm.deltas.slice(-3).every(delta => delta <= 1 / 60))
  assert.ok(Math.abs(vrm.deltas.slice(-3).reduce((sum, delta) => sum + delta, 0) - 0.05) < 1e-8)
  assert.ok(Math.abs(new Euler().setFromQuaternion(vrm.bones.get("chest").quaternion).x) <= 0.0091)
})

test("reduced motion retains a static standing pose and missing optional bones are safe", () => {
  const vrm = rig()
  const update = createStandingIdle(vrm)
  update(1, 0.03, false)
  const staticPose = vrm.bones.get("leftUpperArm").quaternion.clone()
  update(100, 0.03, false)
  assert.ok(vrm.bones.get("leftUpperArm").quaternion.equals(staticPose))
  assert.equal(vrm.bones.get("chest").quaternion.w, 1)
  assert.doesNotThrow(() => createStandingIdle({ update() {} })(1, 0.03))
})

test("physics rebases after placement and settles hidden with bounded steps and stronger damping", () => {
  const events = []
  const settings = { dragForce: 0.2, gravityPower: 0.4, stiffness: 1.2 }
  const strong = { dragForce: 0.8 }
  const vrm = { scene: { updateMatrixWorld: () => events.push("matrix") }, springBoneManager: {
    joints: [{ settings }, { settings: strong }], setInitState: () => events.push("init"),
    reset: () => events.push("reset"), update: delta => events.push(delta),
  } }
  settleStandingPhysics(vrm)
  assert.deepEqual(events.slice(0, 3), ["matrix", "init", "reset"])
  assert.equal(events.filter(value => value === 1 / 60).length, 60)
  assert.equal(settings.dragForce, 0.6)
  assert.equal(strong.dragForce, 0.8)
  assert.equal(settings.gravityPower, 0.4)
  assert.equal(settings.stiffness, 1.2)
  assert.doesNotThrow(() => settleStandingPhysics({}))
})
