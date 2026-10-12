import { Button } from "@/components/ui/button"

import { activitySymbol } from "@/lib/pocketActivityPlayback"
function time(value) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? "" : new Intl.DateTimeFormat("ko-KR", {
    month: "numeric", day: "numeric", hour: "numeric", minute: "2-digit", hour12: false,
  }).format(date)
}
export default function PocketProgressPanel({ progress, characters, disabled }) {
  const { status, busy, error, configure, resume } = progress
  const names = new Map(characters.map(c => [c.id ?? c.characterId, c.name ?? c.characterName]))
  const report = status?.lastReport
  return <section aria-label="시간 체크" className="border-b px-4 py-3 text-xs sm:px-6">
    <div className="flex items-center justify-between gap-2">
      <span className="font-medium">시간 체크</span>
      <Button size="sm" variant={status?.enabled ? "default" : "outline"}
        aria-pressed={Boolean(status?.enabled)} disabled={busy || disabled}
        onClick={() => configure(!status?.enabled)}>{status?.enabled ? "ON" : "OFF"}</Button>
    </div>
    <p className="mt-2 leading-5 text-muted-foreground">복귀할 때 부재 시간을 반영하고 캐릭터 활동을 정리해요.</p>
    {busy && <p role="status" className="mt-2">부재 활동을 확인하는 중…</p>}
    {error && <div className="mt-2 space-y-2"><p role="alert" className="text-destructive">{error}</p>
      <Button size="sm" variant="outline" disabled={busy} onClick={resume}>다시 확인</Button></div>}
    {status?.pauseReason && <p className="mt-2 leading-5 text-muted-foreground">진행 일시정지 · {status.pauseReason}<br/>상황을 정리한 후 OFF → ON으로 다시 켤 수 있어요.</p>}
    {report && <div className="mt-3 space-y-2">
      <p className="font-medium">지난 부재 활동</p>
      <p className="text-muted-foreground">{time(report.from)} → {time(report.to)}</p>
      {report.capped && <p className="text-muted-foreground">최대 3일의 부재 시간을 반영했어요.</p>}
      {report.activities.length ? <ol className="space-y-2">{report.activities.map((activity, index) =>
        <li key={index} className="rounded-md border bg-muted/20 p-2 leading-5">
          <p>{activitySymbol(activity.kind).emoji} {activity.characterIds.map(id => names.get(id) || "캐릭터").join(" · ")}</p>
          <p className="text-muted-foreground">{time(activity.start)} · {activity.location}</p>
          <p>{activity.summary}</p>
        </li>)}</ol> : <p className="text-muted-foreground">별도로 기록할 활동은 없었어요.</p>}
    </div>}
  </section>
}
