import { Euler, Quaternion } from "three"

// Normalized VRM bones let the same rest-relative pose work on different rigs.
// Leave the root and legs untouched so height, placement and foot contact remain stable.
export function createStandingIdle(vrm, phase = 0) {
  const names = ["leftUpperArm", "rightUpperArm", "leftLowerArm", "rightLowerArm", "spine", "chest", "head"]
  const bones = new Map(names.flatMap(name => {
    const bone = vrm.humanoid?.getNormalizedBoneNode?.(name)
    return bone ? [[name, { bone, rest: bone.quaternion.clone() }]] : []
  }))
  const rotation = new Quaternion()
  const angles = new Euler()
  function pose(name, x, y, z) {
    const entry = bones.get(name)
    if (!entry) return
    rotation.setFromEuler(angles.set(x, y, z))
    entry.bone.quaternion.copy(entry.rest).multiply(rotation)
  }
  function update(elapsedSeconds, deltaSeconds, motionEnabled = true, stance = "LIGHT") {
    const time = Number.isFinite(elapsedSeconds) ? elapsedSeconds : 0
    const breath = motionEnabled ? Math.sin(time * 1.65 + phase) : 0
    const sway = motionEnabled ? Math.sin(time * 0.75 + phase) : 0
    if(stance === "ARMS_CROSSED") {
      pose("leftUpperArm", -.55, -.30, -.75)
      pose("rightUpperArm", -.55, .30, .75)
      pose("leftLowerArm", -.9, .25, -1.05)
      pose("rightLowerArm", -.9, -.25, 1.05)
    } else {
      pose("leftUpperArm", breath*.025, 0, -1.35 + breath * 0.025)
      pose("rightUpperArm", -breath*.025, 0, 1.35 - breath * 0.025)
      pose("leftLowerArm", 0, 0.10, -0.05)
      pose("rightLowerArm", 0, -0.10, 0.05)
    }
    pose("spine", 0, 0, sway * 0.012)
    pose("chest", breath * 0.016, 0, sway * 0.008)
    pose("head", breath * 0.008, sway * 0.025, -sway * 0.008)
    const delta = Number.isFinite(deltaSeconds) ? Math.min(0.05, Math.max(0, deltaSeconds)) : 0
    if (delta === 0) vrm.update?.(0)
    else {
      const steps = Math.ceil(delta / (1 / 60))
      for (let step = 0; step < steps; step++) vrm.update?.(delta / steps)
    }
  }
  update(0, 0, false)
  return update
}

// Rebase spring tails AFTER the standing pose, scale, placement and rotation.
// Loaded spring tails otherwise retain their pre-placement world coordinates.
export function settleStandingPhysics(vrm) {
  const springs = vrm.springBoneManager
  if (!springs) return
  for (const joint of springs.joints ?? []) {
    if (joint.settings) joint.settings.dragForce = Math.max(joint.settings.dragForce ?? 0.4, 0.6)
  }
  vrm.scene.updateMatrixWorld(true)
  springs.setInitState()
  springs.reset()
  // Settle gravity off-screen instead of showing the first large relaxation swing.
  for (let step = 0; step < 60; step++) springs.update(1 / 60)
  vrm.scene.updateMatrixWorld(true)
}
