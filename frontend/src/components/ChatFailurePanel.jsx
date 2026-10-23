const modeLabel={REVIEW:"답변 검사",VERIFY:"수정본 재검사",BOOTSTRAP:"이전 대화 보강 검사",UNKNOWN:"검사"}
export default function ChatFailurePanel({records,error}) {
  if(!records.length)return null
  return <section className="shrink-0 border-b px-4 py-2 text-sm" aria-label="장면 검사 검토 기록">
    <p className="font-medium">장면 검사 검토 기록</p>
    <p className="text-xs text-muted-foreground">검사기의 판단이며 실제 오류로 확정된 것은 아닙니다. 자동 재생성하지 않습니다.</p>
    {records.map(row=><details key={row.requestId} open={Boolean(error?.includes(row.requestId))} className="mt-2">
      <summary className="cursor-pointer">{new Date(row.createdAt).toLocaleString("ko-KR")} · {modeLabel[row.mode]??"검사"} · {row.requestId}</summary>
      <ul className="my-2 list-disc pl-5">{(row.reasons??[]).map((text,i)=><li key={i}>{text}</li>)}</ul>
      <p className="font-medium">검사기가 보고한 구체적 충돌</p>
      {(row.conflicts??[]).length?<ol className="list-decimal whitespace-pre-wrap pl-5">{row.conflicts.map((text,i)=><li key={i}>{text}</li>)}</ol>
        :<p>충돌 설명이 비어 있습니다. 위 승인·필수 항목 상태를 확인하세요.</p>}
      {row.candidate && <details className="mt-2"><summary className="cursor-pointer">당시 생성 본문 보기</summary><pre className="mt-2 whitespace-pre-wrap font-sans">{row.candidate}</pre></details>}
      {row.truncated && <p className="text-muted-foreground">긴 진단 내용 일부가 생략됐습니다.</p>}
      {row.diagnosticsSaved===false && <p className="text-destructive">기록 저장에 실패해 현재 화면에서만 확인할 수 있습니다.</p>}
    </details>)}
  </section>
}
