import {useEffect,useRef,useState} from "react"
import {Button} from "@/components/ui/button"
import RpText from "@/components/RpText"
import {createStreamingTypewriter} from "@/lib/streamingTypewriter"

export default function StreamingPocketReply({text,finished=false,onProgress,onSettled}) {
  const [writer]=useState(createStreamingTypewriter)
  const [view,setView]=useState(()=>writer.update(text,performance.now(),finished))
  const callbacks=useRef({onProgress,onSettled})
  useEffect(()=>{callbacks.current={onProgress,onSettled}},[onProgress,onSettled])
  const settled=useRef(false)
  useEffect(()=>{
    let frame
    const motion=window.matchMedia("(prefers-reduced-motion: reduce)")
    function present(value) {setView(value)}
    function tick(now) {
      const value=writer.advance(now);present(value)
      if(value.count<value.total)frame=requestAnimationFrame(tick)
    }
    function skip() {cancelAnimationFrame(frame);present(writer.skip())}
    function visibilityChanged() {if(document.visibilityState!=="visible")skip()}
    const value=writer.update(text,performance.now(),finished);present(value)
    if(motion.matches || document.visibilityState!=="visible")skip()
    else if(value.count<value.total)frame=requestAnimationFrame(tick)
    document.addEventListener("visibilitychange",visibilityChanged)
    return ()=>{cancelAnimationFrame(frame);document.removeEventListener("visibilitychange",visibilityChanged)}
  },[writer,text,finished])
  useEffect(()=>{if(view.count>0)callbacks.current.onProgress?.()},[view.text,view.count])
  useEffect(()=>{
    if(view.done && !settled.current){settled.current=true;callbacks.current.onSettled?.()}
  },[view.done])
  return <>
    <RpText text={view.text} visibleCharacters={view.count}/>
    {view.count<view.total && <Button variant="ghost" size="sm" className="mt-2 text-muted-foreground"
      onClick={()=>setView(writer.skip())}>바로 보기</Button>}
  </>
}
