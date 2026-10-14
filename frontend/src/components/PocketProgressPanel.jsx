import { useEffect, useState } from "react"
import { Button } from "@/components/ui/button"

import { briefActivity } from "@/lib/pocketAbsenceLog"
function time(value) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? "" : new Intl.DateTimeFormat("ko-KR", {
    month: "numeric", day: "numeric", hour: "numeric", minute: "2-digit", hour12: false,
  }).format(date)
}
export default function PocketProgressPanel({ progress, characters, disabled, isDeveloper = false, modelsReady = false, modelError = "" }) {
  const { status, busy, error, configure, resume, simulate } = progress
  const [minutes,setMinutes]=useState(15)
  const [maximumHours,setMaximumHours]=useState(24)
  useEffect(()=>setMaximumHours(status?.maximumAutoHours ?? 24),[status?.maximumAutoHours])
  useEffect(()=>setMinutes(status?.minimumAbsenceMinutes ?? 15),[status?.minimumAbsenceMinutes])
  const names = new Map(characters.map(c => [c.id ?? c.characterId, c.name ?? c.characterName]))
  const report = status?.lastReport
  return <section aria-label="시간 체크" className="border-b px-4 py-3 text-xs sm:px-6">
    <div className="flex items-center justify-between gap-2">
      <span className="font-medium">시간 체크</span>
      <Button size="sm" variant={status?.enabled ? "default" : "outline"}
        aria-pressed={Boolean(status?.enabled)} disabled={busy || disabled || (!status?.enabled && !modelsReady)}
        onClick={() => configure(!status?.enabled)}>{status?.enabled ? "ON" : "OFF"}</Button>
    </div>
    <p className="mt-2 leading-5 text-muted-foreground">복귀할 때 부재 시간을 반영하고 캐릭터 활동을 정리해요.</p>
    {!modelsReady && <p className="mt-2 leading-5 text-muted-foreground">{modelError || "플레이어와 포켓 캐릭터 모델링을 등록해야 시간 체크를 사용할 수 있어요."}</p>}
    <div className="mt-3 flex items-center gap-2">
      <label>부재 시작 <input aria-label="최소 부재 시간(분)" type="number" min="1" max="4320" value={minutes}
        onChange={event=>setMinutes(event.target.value)} className="w-16 rounded border bg-background p-1" />분</label>
      <Button size="sm" variant="outline" disabled={busy || disabled || (status?.enabled && !modelsReady) || !Number.isInteger(Number(minutes)) || Number(minutes)<1 || Number(minutes)>4320}
        onClick={()=>configure(Boolean(status?.enabled),Number(minutes),maximumHours)}>저장</Button>
    </div>
    <div className="mt-3 space-y-2">
      <label htmlFor="absence-maximum-hours" className="flex justify-between"><span>최대 자동 전개</span><span>{maximumHours}시간</span></label>
      <input id="absence-maximum-hours" aria-label="최대 자동 전개 시간" type="range" min="1" max="24" step="1" value={maximumHours}
        disabled={busy || disabled} onChange={event=>setMaximumHours(Number(event.target.value))} className="w-full accent-primary" />
      <div className="flex justify-between text-muted-foreground"><span>1시간</span><span>24시간</span></div>
      <p className="text-muted-foreground">위의 저장을 눌러 적용해요. 부재가 더 길어도 이 시간까지만 전개해요.</p>
    </div>
    {isDeveloper && <details className="mt-3 rounded-md border border-dashed p-2">
      <summary className="cursor-pointer font-medium">부재 시간 테스트</summary>
      <div className="mt-2 flex flex-wrap gap-2">{[1, 2, 3].map(hours =>
        <Button key={hours} size="sm" variant="outline" disabled={busy || disabled || !status?.enabled || !modelsReady}
          onClick={() => simulate(hours)}>{hours}시간 경과</Button>)}</div>
      <p className="mt-2 leading-5 text-muted-foreground">기다리지 않고 부재 활동을 생성해요. 이야기 시각과 대화 기록에 반영돼요.</p>
    </details>}
    {busy && <p role="status" className="mt-2">부재 활동을 확인하는 중…</p>}
    {error && <div className="mt-2 space-y-2"><p role="alert" className="text-destructive">{error}</p>
      <Button size="sm" variant="outline" disabled={busy} onClick={resume}>다시 확인</Button></div>}
    {report && <div className="mt-3 space-y-2">
      <p className="font-medium">지난 부재 활동</p>
      <p className="text-muted-foreground">{time(report.from)} → {time(report.to)}</p>
      {report.capped && <p className="text-muted-foreground">설정한 자동 전개 상한까지만 반영했어요.</p>}
      {report.activities.length ? <ol className="space-y-2">{report.activities.map((activity, index) =>
        <li key={index} className="rounded-md border bg-muted/20 p-2 leading-5">
          <p>{briefActivity(activity)}</p>
        </li>)}</ol> : <p className="text-muted-foreground">별도로 기록할 활동은 없었어요.</p>}
    </div>}
  </section>
}
