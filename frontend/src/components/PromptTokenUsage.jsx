export default function PromptTokenUsage({ usage }) {
  const parts = usage?.promptTokenEstimate;
  if (!parts) return null;
  return <span className="basis-full text-xs text-muted-foreground" title={`${parts.tokenizer} · 메시지 포장 토큰 제외 · 전체 실측값과 합산하지 않음`}>
    항목별 추정 · 시스템 {parts.systemTokens?.toLocaleString()} · 대화 기록 {parts.historyTokens?.toLocaleString()} · 장기기억 {parts.memoryTokens?.toLocaleString()} · 현재 입력 {parts.currentInputTokens?.toLocaleString()} · 응답 형식 {parts.schemaTokens?.toLocaleString()}
  </span>;
}
