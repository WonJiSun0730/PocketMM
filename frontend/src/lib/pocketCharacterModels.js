const CHARACTER_MODEL_REGISTRY = {
  // Temporary development registrations. Keep graphics optional until an
  // official character-model authoring pipeline exists.
  Millie: { url: "/models/milieponygood.vrm", height: 1.6 },
  Marbas: { url: "/models/marbas.vrm", height: 2.23 },
  "밀리": { url: "/models/milieponygood.vrm", height: 1.6 },
  "마르바스": { url: "/models/marbas.vrm", height: 2.23 },
}

export function resolvePocketCharacterModels(cast = []) {
  if (cast.length === 0) return { ready: false, models: [] }

  const models = cast.map(character => {
    const registration = CHARACTER_MODEL_REGISTRY[character.characterName ?? character.name]
    return registration ? { ...registration, character } : null
  })

  return {
    ready: models.every(Boolean),
    models: models.filter(Boolean),
  }
}
