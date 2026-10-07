function toModel(source) {
  if (!source?.modelUrl || !source?.modelHeight) return null
  return {
    url: source.modelUrl,
    height: source.modelHeight,
    source,
  }
}

export function resolvePocketSceneModels({ playerProfile, worldCharacters = [], cast = [] }) {
  const playerModel = toModel(playerProfile)
  const allCharactersReady =
    worldCharacters.length > 0 &&
    worldCharacters.every(character => toModel(character))

  const castModels = cast.map(character => toModel(character))
  const activeCastReady = castModels.every(Boolean)

  return {
    ready: Boolean(playerModel) && allCharactersReady && activeCastReady,
    playerModel,
    castModels: castModels.filter(Boolean),
  }
}
