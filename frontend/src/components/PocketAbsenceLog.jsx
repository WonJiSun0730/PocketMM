import { useEffect, useRef, useState } from "react"
import { createPortal } from "react-dom"
import { Button } from "@/components/ui/button"
import RpText from "@/components/RpText"
import { briefActivity, legacyBriefLog, needsTokenNotice, hideTokenNoticeToday } from "@/lib/pocketAbsenceLog"

function TokenNotice({ onCancel, onConfirm }) {
  const dialog = useRef(null)
  const [hideToday,setHideToday] = useState(false)
  useEffect(()=>{const element=dialog.current;element.showModal();return()=>element.close()},[])
  return createPortal(<dialog ref={dialog} className="rounded-xl border bg-card p-5 text-card-foreground shadow-xl m-auto max-w-[calc(100vw-32px)] w-80 backdrop:bg-black/50"
    aria-labelledby="absence-token-title" onCancel={event=>{event.preventDefault();onCancel()}}>
    <h3 id="absence-token-title" className="font-semibold">자세한 이야기를 펼칠까요?</h3>
    <p className="mt-3 text-sm leading-6">AI가 부재 활동을 소설처럼 풀어 써요. 새로 작성할 때 토큰이 소모되며 사용 비용이 발생해요. 한 번 만든 글은 다시 읽어도 추가로 소모되지 않아요.</p>
    <label className="mt-4 flex items-center gap-2 text-sm"><input type="checkbox" checked={hideToday} onChange={event=>setHideToday(event.target.checked)} />오늘 하루 보지 않기</label>
    <div className="mt-5 flex justify-end gap-2"><Button variant="outline" onClick={onCancel}>취소</Button>
      <Button onClick={()=>{if(hideToday)hideTokenNoticeToday(window.localStorage);onConfirm()}}>이야기 만들기</Button></div>
  </dialog>,document.body)
}
export default function PocketAbsenceLog({ sessionId, reportId, report, fallback = "", index = null, disabled = false, hideBrief = false }) {
  const [view,setView] = useState(null), [busy,setBusy]=useState(false), [error,setError]=useState(""), [notice,setNotice]=useState(false)
  const scope=useRef(null)
  const key=`${sessionId}:${reportId}`
  if(scope.current?.key!==key) scope.current={key}
  const current=scope.current
  const base=`/api/worlds/pockets/${sessionId}/progress/reports/${reportId}`
  async function request(method="GET") {
    const response=await fetch(`${base}${method==="POST" ? "/story" : ""}`,{method,credentials:"include"})
    const data=await response.json().catch(()=>null)
    if(!response.ok)throw new Error(data?.detail || data?.message || "이야기를 불러오지 못했어요. 원본 활동은 그대로 남아 있어요.")
    return data
  }
  useEffect(()=>{
    setView(null);setError("");setBusy(false);setNotice(false)
    let live=true
    async function read() {
      try {
        const result=await request()
        if(!live || scope.current!==current)return
        setView(result)
      } catch(failure) { if(live && scope.current===current)setError(failure.message) }
    }
    if(reportId!=null)read()
    const update=event=>{
      if(event.detail.key===key){setView(event.detail.view);setError("")}
    }
    window.addEventListener("pocket-absence-story",update)
    return()=>{live=false;window.removeEventListener("pocket-absence-story",update)}
  },[key])
  useEffect(()=>{
    if(!view?.generating || busy)return
    let live=true
    const timer=setTimeout(async()=>{
      try {const result=await request();if(live && scope.current===current)setView(result)}
      catch(failure){if(live && scope.current===current)setError(failure.message)}
    },2000)
    return()=>{live=false;clearTimeout(timer)}
  },[key,view,busy])
  async function generate() {
    setNotice(false);setBusy(true);setError("")
    try {
      const result=await request("POST")
      if(scope.current!==current)return
      setView(result)
      window.dispatchEvent(new CustomEvent("pocket-absence-story",{detail:{key,view:result}}))
    } catch(failure) {
      if(scope.current===current){setError(failure.message);try{setView(await request())}catch{ /* original stays visible */ }}
    } finally {if(scope.current===current)setBusy(false)}
  }
  const logs=view?.briefLogs ?? report?.activities?.map(briefActivity) ?? (fallback ? [legacyBriefLog(fallback)] : [])
  const visibleLogs=index==null ? logs : logs.slice(index,index+1)
  const generating=busy || view?.generating
  return <section aria-label="부재 활동 이야기" className="space-y-3">
    {!hideBrief && <div className="space-y-2 text-sm leading-6">{visibleLogs.map((text,i)=><p key={i} className="whitespace-pre-wrap">{text}</p>)}{!logs.length && <p>조용히 시간을 보냈어요.</p>}</div>}
    {view?.narrative && <><hr className="my-4 border-border" /><RpText text={view.narrative} /></>}
    {generating && <p role="status" className="text-sm text-muted-foreground">그동안의 이야기를 펼치고 있어요…</p>}
    {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
    {!view?.narrative && <Button size="sm" variant="outline" disabled={disabled || generating || reportId==null}
      onClick={()=>{if(needsTokenNotice(window.localStorage))setNotice(true);else generate()}}>자세히 보기</Button>}
    {view?.activities && <details className="text-xs text-muted-foreground"><summary className="cursor-pointer">원본 활동 기록</summary>
      <div className="mt-2 space-y-2">{view.activities.map((activity,i)=><p key={i}>{activity.start} ~ {activity.end} · {activity.location}<br/>{activity.summary}</p>)}</div></details>}
    {notice && <TokenNotice onCancel={()=>setNotice(false)} onConfirm={generate} />}
  </section>
}
