import { useEffect, useMemo, useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import RpText from "@/components/RpText"
import { roleplaySegments,visibleCharacterCount } from "@/lib/typedRoleplay"
export default function TypedPocketReply({text,animate=false,children,onProgress}) {
  const parts=useMemo(()=>roleplaySegments(text),[text])
  const total=parts.reduce((sum,part)=>sum+part.characters.length,0)
  const [count,setCount]=useState(animate ? 0 : total)
  const skipped=useRef(false),progress=useRef(onProgress)
  progress.current=onProgress
  useEffect(()=>{
    skipped.current=false
    if (!animate || window.matchMedia("(prefers-reduced-motion: reduce)").matches) {setCount(total);return}
    setCount(0)
    const start=performance.now();let frame
    function tick(now){
      if(skipped.current)return
      const next=visibleCharacterCount(now-start,total);setCount(next)
      if(next<total)frame=requestAnimationFrame(tick)
    }
    frame=requestAnimationFrame(tick)
    return ()=>cancelAnimationFrame(frame)
  },[text,animate,total])
  useEffect(()=>{if(animate && count>0)progress.current?.()},[count,animate])
  const typing=animate && count<total
  return <>
    {typing && <span className="sr-only">{parts.flatMap(part=>part.characters).join("")}</span>}
    <div aria-hidden={typing || undefined}><RpText text={text} visibleCharacters={typing ? count : Infinity}/></div>
    {typing ? <Button variant="ghost" size="sm" className="mt-2 text-muted-foreground" onClick={()=>{skipped.current=true;setCount(total)}}>바로 보기</Button> : children}
  </>
}
