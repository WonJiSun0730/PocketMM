export default function AiCallUsage({ calls = [], diagnostics }) {
  const labels = { ROLEPLAY: '본 대화', INITIAL_SCENE: '초기 장면', SCENE_SETUP: '장면 분석', MEMORY_EXTRACTION: '기억 추출' };
  const groups = new Map();
  for (const call of calls) {
    const group = groups.get(call.purpose) || { count: 0, cost: 0, unknown: false };
    group.count++;
    if (call.usage?.estimatedCostUsd == null) group.unknown = true;
    else group.cost += Number(call.usage.estimatedCostUsd);
    groups.set(call.purpose, group);
  }
  return <span className="basis-full text-xs text-muted-foreground">
    {groups.size > 0 && <span>서버 시작 이후 최근 최대 1,000건 · {Array.from(groups, ([purpose, g]) => `${labels[purpose] || purpose} ${g.count}회 $${g.cost.toFixed(6)}${g.unknown ? ' + 산정 불가 호출 있음' : ''}`).join(' · ')} · 보조 기능 전체 청구액은 아님</span>}
    {diagnostics && <span className="block">캐시: 고정 부분만 쓰기 · 고정 지침 {diagnostics.sameStablePrefixAsPrevious ? '직전 요청과 동일' : '첫 요청 또는 변경'} · {diagnostics.stableCharacters?.toLocaleString()}자</span>}
  </span>;
}
