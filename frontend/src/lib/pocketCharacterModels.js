function toModel(source) {
  if (typeof source?.modelUrl !== "string" || !source.modelUrl.trim() || !Number.isFinite(Number(source.modelHeight)) || Number(source.modelHeight)<=0) return null
  return {
    url: source.modelUrl,
    height: Number(source.modelHeight),
    source,
  }
}

export function resolvePocketSceneModels({ playerProfile, worldCharacters = [], cast = [] }) {
  const playerModel = toModel(playerProfile)
  const allCharactersReady =
    worldCharacters.every(character => toModel(character))

  const castModels = cast.map(character => toModel(character))
  const activeCastReady = castModels.every(Boolean)

  return {
    ready: Boolean(playerModel) && worldCharacters.length>0 && allCharactersReady && activeCastReady,
    blockedReason: !playerProfile ? "플레이어를 선택해 주세요." : !playerModel ? "플레이어 모델링을 등록해 주세요."
      : !worldCharacters.length || !allCharactersReady || !activeCastReady ? "포켓에 등록된 캐릭터 모델링을 모두 등록해 주세요." : "",
    playerModel,
    castModels: castModels.filter(Boolean),
  }
}
