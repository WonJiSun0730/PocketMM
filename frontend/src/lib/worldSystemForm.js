export function newWorldSystem() {
  return { name: "", changeCondition: "", minValue: 0, maxValue: 100, initialValue: 0, states: [] }
}

function integer(value, label) {
  if (value == null || String(value).trim() === "") throw new Error(`${label}을 입력해주세요.`)
  const number = Number(value)
  if (!Number.isInteger(number) || number < -2147483648 || number > 2147483647) throw new Error(`${label}에는 범위 내 정수를 입력해주세요.`)
  return number
}

export function prepareWorldSystems(systems) {
  return systems.filter(system => system.name?.trim()).map(system => {
    const name = system.name.trim()
    if (name.length > 100) throw new Error("시스템 이름은 100자 이내로 입력해주세요.")
    const minValue = integer(system.minValue, `${name} 하한`)
    const maxValue = integer(system.maxValue, `${name} 상한`)
    const initialValue = integer(system.initialValue, `${name} 초기값`)
    if (minValue >= maxValue) throw new Error(`${name}: 하한은 상한보다 작아야 해요.`)
    if (initialValue < minValue || initialValue > maxValue) throw new Error(`${name}: 초기값은 하한과 상한 사이여야 해요.`)
    const states = (system.states ?? []).filter(state => state.label?.trim()).map(state => {
      const low = integer(state.minValue, `${name} 상태 하한`)
      const high = integer(state.maxValue, `${name} 상태 상한`)
      if (low > high || low < minValue || high > maxValue) throw new Error(`${name}: 상태 범위를 시스템 하한·상한 안에 설정해주세요.`)
      return { minValue: low, maxValue: high, label: state.label.trim() }
    })
    return { name, changeCondition: system.changeCondition ?? "", minValue, maxValue, initialValue, states }
  })
}
